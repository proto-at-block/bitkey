package build.wallet.statemachine.fwup

import bitkey.account.AccountConfigServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.FwupMcuEventTrackerContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.FWUP
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_DETECTED
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_MCU_UPDATE_FAILED
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.*
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.nfc.*
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class FwupNfcSessionUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val nfcTransactor = NfcTransactorMock(turbines::create)
  val firmwareDataService = FirmwareDataServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val fwupDataDaoProvider = FwupDataDaoProviderMock(turbines::create)
  val signatureVerifierTurbine = turbines.create<VerifyEcdsaCall>("verifyEcdsa calls")
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val nfcSessionRetryAttemptsFeatureFlag = NfcSessionRetryAttemptsFeatureFlag(FeatureFlagDaoFake())
  val hardwareConfirmationUiStateMachine =
    object : HardwareConfirmationUiStateMachine,
      ScreenStateMachineMock<HardwareConfirmationUiProps>("hardware-confirmation") {}

  val stateMachine =
    FwupNfcSessionUiStateMachineImpl(
      enableNfcNavigator = EnableNfcNavigatorMock(),
      eventTracker = eventTracker,
      fwupProgressCalculator = FwupProgressCalculatorMock(),
      deviceInfoProvider = deviceInfoProvider,
      nfcReaderCapability = NfcReaderCapabilityMock(),
      nfcTransactor = nfcTransactor,
      fwupDataDaoProvider = fwupDataDaoProvider,
      firmwareDataService = firmwareDataService,
      accountConfigService = accountConfigService,
      keyboxDao = keyboxDao,
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine),
      nfcSessionRetryAttemptsFeatureFlag = nfcSessionRetryAttemptsFeatureFlag,
      hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine
    )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onDoneCalls = turbines.create<Unit>("onDone calls")
  val onErrorCalls = turbines.create<NfcException>("onError calls")

  val props =
    FwupNfcSessionUiProps(
      onBack = { onBackCalls.add(Unit) },
      onDone = { onDoneCalls.add(Unit) },
      transactionType = FwupTransactionType.StartFromBeginning,
      onError = { error, _, _ -> onErrorCalls.add(error) }
    )

  beforeTest {
    accountConfigService.reset()
    deviceInfoProvider.reset()
    nfcTransactor.reset()
    keyboxDao.reset()
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock.copy(
      firmwareUpdateState = PendingUpdate(immutableListOf(McuFwupDataMock_W1_CORE))
    )
  }

  test("happy path") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Successfully updated")
        status.shouldBeTypeOf<Success>()
        onCancel.shouldBeNull()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      firmwareDataService.firmwareData.value.shouldBe(
        FirmwareDataUpToDateMock.copy(
          firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(
            version = McuFwupDataMock_W1_CORE.version
          )
        )
      )
      onDoneCalls.awaitItem()
    }
  }

  test("in progress cancel") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
        onCancel.shouldNotBeNull().invoke()
      }

      nfcTransactor.transactCalls.awaitItem()

      // TODO(W-4584): Make testing this better simulate reality where [NfcTransactor] wouldn't
      // have responded in this case.
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()

      onBackCalls.awaitItem()
    }
  }

  test("onTagConnected") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls =
        nfcTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<NfcSession.Parameters>()

      // TODO(W-4584): Make testing this better simulate reality where [NfcTransactor] wouldn't
      // have responded in this case.
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()

      transactCalls.onTagConnected(NfcSessionFake())
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, NFC_DETECTED, FWUP)
      )

      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Updating...")
        status.shouldBeTypeOf<InProgress>()
      }

      // Calling onTagConnected sets state back to InNfcSessionUiState, which triggers
      // a new NFC transaction. Consume it to avoid unconsumed events error.
      nfcTransactor.transactCalls.awaitItem()

      // The new transaction also completes, emitting a Success screen
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("onTagDisconnected") {
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      val transactCalls =
        nfcTransactor.transactCalls.awaitItem()
          .shouldBeTypeOf<NfcSession.Parameters>()

      // TODO(W-4584): Make testing this better simulate reality where [NfcTransactor] wouldn't
      // have responded in this case.
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()

      transactCalls.onTagDisconnected()
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Device no longer detected,\nhold device to phone")
        status.shouldBeTypeOf<LostConnection>()
      }

      // Calling onTagDisconnected sets state back to InNfcSessionUiState, which triggers
      // a new NFC transaction. Consume it to avoid unconsumed events error.
      nfcTransactor.transactCalls.awaitItem()

      // The new transaction also completes, emitting a Success screen
      awaitBody<FwupNfcBodyModel>()
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  test("failure - user cancellation") {
    nfcTransactor.transactResult = Err(NfcException.IOSOnly.UserCancellation())
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      onBackCalls.awaitItem()
    }
  }

  test("failure - other") {
    nfcTransactor.transactResult = Err(NfcException.CommandError())
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.text.shouldBe("Hold device here behind phone")
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  // W3 Two-Tap Confirmation Flow Tests

  test("W3 confirmation flow - shows hardware confirmation screen") {
    // Simulate W3 two-tap flow by returning RequiresConfirmation with a mock fetchResult
    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      // Verify the HardwareConfirmationUiStateMachine is shown (via BodyModelMock)
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        // Verify the props are correctly wired
        onBack.shouldNotBeNull()
        onConfirm.shouldNotBeNull()
      }
    }
  }

  test("W3 confirmation flow - onConfirm starts new NFC session for continuation") {
    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      // Get the confirmation screen and invoke onConfirm
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      // After confirmation, should transition back to NFC session (searching state)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // A new NFC transaction should be started for the continuation
      nfcTransactor.transactCalls.awaitItem()

      // The new transaction also completes (with RequiresConfirmation again since mock
      // is still set to that), emitting another confirmation screen
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation")
    }
  }

  test("W3 confirmation flow - onBack cancels the flow") {
    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      nfcTransactor.transactCalls.awaitItem()

      // Get the confirmation screen and invoke onBack
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onBack()
      }

      // onBack should trigger the props.onBack callback
      onBackCalls.awaitItem()
    }
  }
})
