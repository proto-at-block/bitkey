package build.wallet.statemachine.recovery.losthardware

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachine

@BitkeyInject(ActivityScope::class)
class LostHardwareRecoveryUiStateMachineImpl(
  private val initiatingLostHardwareRecoveryUiStateMachine:
    InitiatingLostHardwareRecoveryUiStateMachine,
  private val recoveryInProgressUiStateMachine: RecoveryInProgressUiStateMachine,
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
) : LostHardwareRecoveryUiStateMachine {
  @Composable
  override fun model(props: LostHardwareRecoveryProps): ScreenModel {
    val hardwareRecovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    /**
     * We use this to manually track if this flow was previously in a recovery
     * when it transitions to no recovery because that indicates that the
     * flow completed because it indicates a transition of the recovery object from nonnull -> null.
     *
     * We need this workaround for now because we can't decouple the UI we want to show (a success
     * screen and then exit the flow) from the underlying data changes – at the success screen, the
     * recovery object still exists, and the user action of exiting from that success screen clears
     * the recovery object (instead of closing the screen). So here, we are listening to that
     * clearing of the recovery that happens at the service level and performing the UI
     * action we want – closing the screen.
     *
     * TODO(W-4008): revisit the logic around exiting these screens.
     */
    var recoveryWasInProgress by remember { mutableStateOf(false) }

    return when (val stillRecovering = hardwareRecovery) {
      is Recovery.StillRecovering -> {
        require(stillRecovering.factorToRecover == PhysicalFactor.Hardware)

        // Get the old app global auth key from the persisted recovery state if available,
        // otherwise fall back to the current keybox's auth key.
        // After auth key rotation, the keybox's auth key will be the NEW key, but the
        // recovery state will have the original key persisted.
        val oldAppGlobalAuthKey = stillRecovering.originalAppGlobalAuthKey
          ?: props.account.keybox.activeAppKeyBundle.authKey

        val recoveryInProgressData = recoveryInProgressDataStateMachine.model(
          props = RecoveryInProgressProps(
            recovery = stillRecovering,
            oldAppGlobalAuthKey = oldAppGlobalAuthKey
          )
        )

        // Here we mark that a recovery in progress. See documentation on this variable.
        LaunchedEffect("lost-hw-recovery-was-in-progress") {
          recoveryWasInProgress = true
        }

        recoveryInProgressUiStateMachine.model(
          props = RecoveryInProgressUiProps(
            presentationStyle = Modal,
            recoveryInProgressData = recoveryInProgressData,
            onExit = props.onExit,
            onComplete = props.onComplete
          )
        )
      }

      else -> {
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
              instructionsStyle = props.instructionsStyle,
              onFoundHardware = props.onFoundHardware,
              onExit = props.onExit
            )
          )
        }
      }
    }
  }
}
