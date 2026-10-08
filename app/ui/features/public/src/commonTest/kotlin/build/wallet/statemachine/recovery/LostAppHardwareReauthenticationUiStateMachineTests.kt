package build.wallet.statemachine.recovery

import bitkey.account.HardwareType
import build.wallet.f8e.auth.InitiateAuthenticationSuccessMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.AwaitingHardwareReauthenticationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.HardwareReauthenticationPurpose.Cancellation
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LostAppHardwareReauthenticationUiStateMachineTests : FunSpec({
  val nfcSessionUiStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}
  val nfcConfirmableSessionUiStateMachine =
    NfcConfirmableSessionUiStateMachineMock("nfc-confirmable")
  val stateMachine = LostAppHardwareReauthenticationUiStateMachineImpl(
    nfcSessionUiStateMachine = nfcSessionUiStateMachine,
    nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine
  )

  fun cancellationData(hardwareType: HardwareType) = AwaitingHardwareReauthenticationData(
    authChallenge = InitiateAuthenticationSuccessMock,
    hardwareType = hardwareType,
    purpose = Cancellation,
    onSignedChallenge = {},
    onCancel = {}
  )

  test("W1 cancellation uses cancellation copy and telemetry") {
    stateMachine.test(
      LostAppHardwareReauthenticationUiProps(
        data = cancellationData(HardwareType.W1),
        presentationStyle = ScreenPresentationStyle.Modal
      )
    ) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {
        segment.shouldBe(RecoverySegment.DelayAndNotify.LostApp.Cancellation)
        actionDescription.shouldBe("Reauthenticating with hardware to cancel lost app recovery")
      }
    }
  }

  test("W3 cancellation uses cancellation copy and telemetry") {
    stateMachine.test(
      LostAppHardwareReauthenticationUiProps(
        data = cancellationData(HardwareType.W3),
        presentationStyle = ScreenPresentationStyle.Modal
      )
    ) {
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>("nfc-confirmable") {
        segment.shouldBe(RecoverySegment.DelayAndNotify.LostApp.Cancellation)
        actionDescription.shouldBe("Reauthenticating with hardware to cancel lost app recovery")
        confirmationResultContent.pendingSubline.shouldBe(
          "Approve on your Bitkey device to cancel recovery."
        )
      }
    }
  }
})
