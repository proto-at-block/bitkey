package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsNfcResult
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResetFingerprintsUiStateMachineImplTests : FunSpec({

  val nfcSessionUiStateMachine =
    object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session"
    ) {}

  val stateMachine = ResetFingerprintsUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUiStateMachine,
    clock = ClockFake(),
    durationFormatter = DurationFormatterFake()
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete calls")
  val onCancelCalls = turbines.create<Unit>("onCancel calls")

  val props = ResetFingerprintsProps(
    onComplete = { onCompleteCalls += Unit },
    onCancel = { onCancelCalls += Unit }
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  test("initial state shows confirmation body") {
    stateMachine.test(props) {
      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        header
          .shouldNotBeNull()
          .headline.shouldBe("Start fingerprint reset")
      }
    }
  }

  test("confirm reset shows tap device sheet") {
    stateMachine.test(props) {
      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        header.shouldNotBeNull()
        header.headline.shouldBe("Wake your Bitkey device")
      }
    }
  }

  test("dismissing tap device sheet returns to confirmation") {
    stateMachine.test(props) {
      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        onDismiss()
      }

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("confirming tap device sheet transitions to NFC state") {
    stateMachine.test(props) {
      awaitBody<ResetFingerprintsConfirmationBodyModel> { primaryButton!!.onClick() }
      awaitSheet<ResetFingerprintsConfirmationSheetModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>>(id = nfcSessionUiStateMachine.id) {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(ResetFingerprintsNfcResult.Success)
      }

      awaitBody<AppDelayNotifyInProgressBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Fingerprint reset in progress...")
      }
    }
  }

  test("clicking close on confirmation body calls onCancel") {
    stateMachine.test(props) {
      awaitBody<ResetFingerprintsConfirmationBodyModel> {
        val accessory = toolbar?.leadingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
        accessory.model.onClick?.invoke()
      }

      onCancelCalls.awaitItem()
    }
  }
})
