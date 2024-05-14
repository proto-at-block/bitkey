package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EnrollingFingerprintUiStateMachineImplTests : FunSpec({
  val stateMachine = EnrollingFingerprintUiStateMachineImpl(
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "nfc fingerprints"
      ) {}
  )

  val onCancelCalls = turbines.create<Unit>("on cancel calls")
  val onSuccessCalls = turbines.create<EnrolledFingerprints>("on success calls")

  val props = EnrollingFingerprintProps(
    account = FullAccountMock,
    onCancel = { onCancelCalls += Unit },
    onSuccess = { onSuccessCalls += it },
    fingerprintHandle = FingerprintHandle(index = 2, label = "Right Thumb")
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  test("start enrollment then cancel") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<*>> {
        onCancel()
      }

      onCancelCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cancelling confirmation goes back to instructions") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint()
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        onCancel()
      }
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up another fingerprint")
      }
    }
  }

  test("backing out of instructions invokes props.onCancel") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Boolean>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(true)
      }

      // See the fingerprint instructions and tap the "Save fingerprint" button, which prompts to
      // tap again for enrollment status
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up another fingerprint")
        onBack.invoke()
      }

      onCancelCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("successful enrollment returns latest fingerprints") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint()

      val enrolledFingerprints = EnrolledFingerprints(
        maxCount = 3,
        fingerprintHandles = immutableListOf(
          FingerprintHandle(index = 0, label = "Left Thumb"),
          FingerprintHandle(index = 2, label = "Right Thumb")
        )
      )
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Complete(enrolledFingerprints))
      }

      onSuccessCalls.awaitItem().shouldBe(enrolledFingerprints)
    }
  }

  test("enrollment incomplete shows an error") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint()

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Incomplete)
      }

      with(awaitItem()) {
        // See the error overlay
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>().apply {
            header.shouldNotBeNull()
              .headline.shouldBe("Incomplete Fingerprint Scan")

            // Close error overlay
            clickPrimaryButton()
          }
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up another fingerprint")
      }
    }
  }
})

private suspend fun StateMachineTester<EnrollingFingerprintProps, ScreenModel>.startEnrollmentAndTapSaveFingerprint() =
  apply {
    // Start the fingerprint enrollment
    awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Boolean>> {
      onSuccess(true)
    }

    // See the fingerprint instructions and tap the "Save fingerprint" button, which prompts to
    // tap again for enrollment status
    awaitScreenWithBody<PairNewHardwareBodyModel> {
      header.headline.shouldBe("Set up another fingerprint")
      primaryButton.apply {
        text.shouldBe("Save fingerprint")
        onClick.invoke()
      }
    }
  }
