package build.wallet.statemachine.fwup

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
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
  val fwupDataDao = FwupDataDaoMock(turbines::create)
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
      fwupDataDao = fwupDataDao,
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
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    // Simulate W3 two-tap flow by returning RequiresConfirmation with a mock fetchResult
    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First tap: shouldLock = false for W3 initial transaction (user needs to confirm on device)
      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(false)

      // Verify the HardwareConfirmationUiStateMachine is shown (via BodyModelMock)
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        // Verify the props are correctly wired
        onBack.shouldNotBeNull()
        onConfirm.shouldNotBeNull()
      }
    }
  }

  test("W3 confirmation flow - onConfirm starts new NFC session for continuation") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First tap: shouldLock = false (W3 initial transaction)
      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(false)

      // Get the confirmation screen and invoke onConfirm
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      // After confirmation, should transition back to NFC session (searching state)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // Second tap: shouldLock = true (continuation transaction)
      val continuationParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      continuationParams.shouldLock.shouldBe(true)
      continuationParams.nfcFlowName.shouldBe("fwup-confirmation")

      // The new transaction also completes (with RequiresConfirmation again since mock
      // is still set to that), emitting another confirmation screen
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation")
    }
  }

  test("W3 confirmation flow - onBack cancels the flow") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)

    val mockFetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean> =
      { _, _ -> HardwareInteraction.Completed(true) }

    nfcTransactor.transactResult = Ok(FwupTransactionResult.RequiresConfirmation(mockFetchResult))
    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First tap: shouldLock = false (W3 initial transaction)
      val initialParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      initialParams.shouldLock.shouldBe(false)

      // Get the confirmation screen and invoke onBack
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onBack()
      }

      // onBack should trigger the props.onBack callback
      onBackCalls.awaitItem()
    }
  }

  // W3 Sequential MCU Update Tests

  test("W3 sequential update - UXC then CORE completes successfully") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(props) {
      // Initial searching state for UXC (first MCU)
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First NFC transaction (UXC MCU) - completes instantly
      nfcTransactor.transactCalls.awaitItem()

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        completedIndex.shouldBe(1)
        totalMcus.shouldBe(2)
        onContinue()
      }

      // Second NFC transaction (CORE MCU) starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // After both MCUs complete, should show success
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
        status.text.shouldBe("Successfully updated")
      }

      // Verify completion event is tracked (only once for entire flow)
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))

      // Verify firmware version is updated with W3 CORE version
      firmwareDataService.firmwareData.value.firmwareDeviceInfo?.version.shouldBe(
        McuFwupDataMock_W3_CORE.version
      )

      onDoneCalls.awaitItem()
    }
  }

  test("W3 sequential update - both MCUs are processed in order") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Ok(FwupTransactionResult.Completed)

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // First NFC transaction (UXC MCU)
      val uxcParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      uxcParams.nfcFlowName.shouldBe("fwup")

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        onContinue()
      }

      // Second NFC transaction (CORE MCU) starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      val coreParams = nfcTransactor.transactCalls.awaitItem()
        .shouldBeTypeOf<NfcSession.Parameters>()
      coreParams.nfcFlowName.shouldBe("fwup")

      // After both MCUs complete, success
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Success>()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_FWUP_COMPLETE))
      onDoneCalls.awaitItem()
    }
  }

  // W3 Partial Failure Tests

  test("W3 update - failure during UXC MCU calls onError with UXC context") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3
    nfcTransactor.transactResult = Err(NfcException.CommandError())

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // UXC transaction fails (first MCU)
      nfcTransactor.transactCalls.awaitItem()

      // Should track failure with UXC context
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.UXC)
      )

      // Should call onError
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }

  test("W3 update - failure during CORE MCU after UXC succeeds calls onError with CORE context") {
    // Configure as W3 hardware
    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock_W3

    // Queue results upfront: UXC succeeds, then CORE fails
    nfcTransactor.queueTransactResults(
      listOf(
        Ok(FwupTransactionResult.Completed), // UXC
        Err(NfcException.CommandError()) // CORE
      )
    )

    stateMachine.test(props) {
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }

      // UXC transaction succeeds (first MCU)
      nfcTransactor.transactCalls.awaitItem()

      // After first MCU completes, show intermediate screen prompting for next MCU
      awaitBody<FwupNextComponentReadyModel> {
        onContinue()
      }

      // CORE transaction starts after user taps continue
      awaitBody<FwupNfcBodyModel> {
        status.shouldBeTypeOf<Searching>()
      }
      nfcTransactor.transactCalls.awaitItem()

      // Should track failure with CORE context
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_FWUP_MCU_UPDATE_FAILED, context = FwupMcuEventTrackerContext.CORE)
      )

      // Should call onError
      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }
})
