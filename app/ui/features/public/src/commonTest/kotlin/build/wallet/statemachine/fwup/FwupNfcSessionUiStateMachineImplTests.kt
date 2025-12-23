package build.wallet.statemachine.fwup

import bitkey.account.AccountConfigServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.FWUP
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_DETECTED
import build.wallet.analytics.v1.Action.ACTION_APP_FWUP_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.encrypt.SignatureVerifierMock.VerifyEcdsaCall
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.*
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.nfc.*
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.statemachine.platform.nfc.EnableNfcNavigatorMock
import build.wallet.statemachine.ui.awaitBody
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
      signatureVerifier = SignatureVerifierMock(signatureVerifierTurbine)
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
      firmwareUpdateState = PendingUpdate(FwupDataMock)
    )
  }

  test("happy path") {
    nfcTransactor.transactResult = Ok(Unit)
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
            version = FwupDataMock.version
          )
        )
      )
      onDoneCalls.awaitItem()
    }
  }

  test("in progress cancel") {
    nfcTransactor.transactResult = Ok(Unit)
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
    nfcTransactor.transactResult = Ok(Unit)
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
    }
  }

  test("onTagDisconnected") {
    nfcTransactor.transactResult = Ok(Unit)
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

      onErrorCalls.awaitItem().shouldBeTypeOf<NfcException.CommandError>()
    }
  }
})
