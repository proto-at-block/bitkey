package build.wallet.statemachine.recovery

import androidx.compose.runtime.Composable
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_AUTH
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.platform.lostAppRecoverySignChallenge
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.platform.signChallenge
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.AwaitingHardwareReauthenticationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.HardwareReauthenticationPurpose.Cancellation
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent

interface LostAppHardwareReauthenticationUiStateMachine :
  StateMachine<LostAppHardwareReauthenticationUiProps, ScreenModel>

data class LostAppHardwareReauthenticationUiProps(
  val data: AwaitingHardwareReauthenticationData,
  val presentationStyle: ScreenPresentationStyle,
)

@BitkeyInject(ActivityScope::class)
class LostAppHardwareReauthenticationUiStateMachineImpl(
  private val nfcSessionUiStateMachine: NfcSessionUIStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
) : LostAppHardwareReauthenticationUiStateMachine {
  @Composable
  override fun model(props: LostAppHardwareReauthenticationUiProps): ScreenModel {
    val isCancellation = props.data.purpose == Cancellation
    val segment = if (isCancellation) {
      RecoverySegment.DelayAndNotify.LostApp.Cancellation
    } else {
      RecoverySegment.DelayAndNotify.LostApp.Completion
    }
    val actionDescription = if (isCancellation) {
      "Reauthenticating with hardware to cancel lost app recovery"
    } else {
      "Reauthenticating with hardware to complete lost app recovery"
    }
    val confirmationSubline = if (isCancellation) {
      "Approve on your Bitkey device to cancel recovery."
    } else {
      "Approve on your Bitkey device to continue recovery."
    }

    return when (props.data.hardwareType) {
      HardwareType.W1 -> nfcSessionUiStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            commands.signChallenge(session, props.data.authChallenge.challenge)
          },
          onSuccess = props.data.onSignedChallenge,
          onCancel = props.data.onCancel,
          hardwareVerification = NotRequired,
          shouldLock = false,
          segment = segment,
          actionDescription = actionDescription,
          eventTrackerContext = APP_DELAY_NOTIFY_SIGN_AUTH,
          screenPresentationStyle = props.presentationStyle,
          hardwareTypeOverride = HardwareType.W1
        )
      )

      HardwareType.W3 -> nfcConfirmableSessionUiStateMachine.model(
        NfcConfirmableSessionUIStateMachineProps(
          session = { session, commands ->
            commands.requireW3(session).lostAppRecoverySignChallenge(
              session,
              props.data.authChallenge.challenge
            )
          },
          onSuccess = props.data.onSignedChallenge,
          onCancel = props.data.onCancel,
          hardwareVerification = NotRequired,
          shouldLock = false,
          segment = segment,
          actionDescription = actionDescription,
          eventTrackerContext = APP_DELAY_NOTIFY_SIGN_AUTH,
          screenPresentationStyle = props.presentationStyle,
          confirmationContent = HardwareConfirmationContent.LostAppRecoverySignChallenge,
          confirmationResultContent = ConfirmationResultContent(
            pendingHeadline = "Confirm this is your account",
            pendingSubline = confirmationSubline
          ),
          hardwareTypeOverride = HardwareType.W3
        )
      )
    }
  }
}
