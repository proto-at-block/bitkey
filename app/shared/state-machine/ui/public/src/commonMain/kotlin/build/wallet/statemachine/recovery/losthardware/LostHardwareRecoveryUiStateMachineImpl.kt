package build.wallet.statemachine.recovery.losthardware

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachine

@BitkeyInject(ActivityScope::class)
class LostHardwareRecoveryUiStateMachineImpl(
  private val initiatingLostHardwareRecoveryUiStateMachine:
    InitiatingLostHardwareRecoveryUiStateMachine,
  private val recoveryInProgressUiStateMachine: RecoveryInProgressUiStateMachine,
) : LostHardwareRecoveryUiStateMachine {
  @Composable
  override fun model(props: LostHardwareRecoveryProps): ScreenModel {
    /**
     * We use this to manually track if this flow was previously in a recovery
     * when it transitions to [InitiatingLostHardwareRecoveryData] because that indicates that the
     * flow completed because it indicates a transition of the recovery object from nonnull -> null.
     *
     * We need this workaround for now because we can't decouple the UI we want to show (a success
     * screen and then exit the flow) from the underlying data changes – at the success screen, the
     * recovery object still exists, and the user action of exiting from that success screen clears
     * the recovery object (instead of closing the screen). So here, we are listening to that
     * clearing of the recovery that happens at the data state machine level and performing the UI
     * action we want – closing the screen.
     *
     * TODO(W-4008): revisit the logic around exiting these screens.
     */
    var recoveryWasInProgress by remember { mutableStateOf(false) }

    return when (val lostHardwareRecoveryData = props.lostHardwareRecoveryData) {
      is InitiatingLostHardwareRecoveryData -> {
        if (recoveryWasInProgress) {
          // Exit since the recovery has been resolved. See documentation on this variable.
          LaunchedEffect("leaving-lost-hw-recovery-in-progress") {
            props.onExit()
          }
          LoadingBodyModel(
            id = null
          ).asScreen(props.screenPresentationStyle)
        } else {
          initiatingLostHardwareRecoveryUiStateMachine.model(
            props = InitiatingLostHardwareRecoveryProps(
              account = props.account,
              screenPresentationStyle = props.screenPresentationStyle,
              initiatingLostHardwareRecoveryData = lostHardwareRecoveryData,
              instructionsStyle = props.instructionsStyle,
              onFoundHardware = props.onFoundHardware,
              onExit = props.onExit
            )
          )
        }
      }
      is LostHardwareRecoveryInProgressData -> {
        // Here we mark that a recovery in progress. See documentation on this variable.
        LaunchedEffect("lost-hw-recovery-was-in-progress") {
          recoveryWasInProgress = true
        }
        recoveryInProgressUiStateMachine.model(
          props = RecoveryInProgressUiProps(
            presentationStyle = Modal,
            recoveryInProgressData = lostHardwareRecoveryData.recoveryInProgressData,
            fullAccountConfig = props.account.keybox.config,
            onExit = props.onExit,
            onComplete = props.onComplete
          )
        )
      }
    }
  }
}
