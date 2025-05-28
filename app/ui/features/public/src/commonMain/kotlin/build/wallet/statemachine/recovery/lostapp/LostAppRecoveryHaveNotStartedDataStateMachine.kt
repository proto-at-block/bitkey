package build.wallet.statemachine.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachine

/**
 * A UI state machine when the customer has engaged with the Lost App recovery process but has not yet
 * officially started a recovery on the server.
 */
interface LostAppRecoveryHaveNotStartedUiStateMachine :
  StateMachine<LostAppRecoveryHaveNotStartedUiProps, ScreenModel>

data class LostAppRecoveryHaveNotStartedUiProps(
  val notUndergoingRecoveryData: LostAppRecoveryData.LostAppRecoveryHaveNotStartedData,
)

@BitkeyInject(ActivityScope::class)
class LostAppRecoveryHaveNotStartedUiStateMachineImpl(
  private val initiatingLostAppRecoveryUiStateMachine: InitiatingLostAppRecoveryUiStateMachine,
  private val fullAccountCloudBackupRestorationUiStateMachine:
    FullAccountCloudBackupRestorationUiStateMachine,
) : LostAppRecoveryHaveNotStartedUiStateMachine {
  @Composable
  override fun model(props: LostAppRecoveryHaveNotStartedUiProps): ScreenModel {
    return when (props.notUndergoingRecoveryData) {
      is AttemptingCloudRecoveryLostAppRecoveryDataData -> {
        fullAccountCloudBackupRestorationUiStateMachine.model(
          props = FullAccountCloudBackupRestorationUiProps(
            backup = props.notUndergoingRecoveryData.cloudBackup,
            onExit = props.notUndergoingRecoveryData.rollback,
            onRecoverAppKey = props.notUndergoingRecoveryData.onRecoverAppKey
          )
        )
      }

      is InitiatingLostAppRecoveryData ->
        initiatingLostAppRecoveryUiStateMachine.model(
          InitiatingLostAppRecoveryUiProps(
            initiatingLostAppRecoveryData = props.notUndergoingRecoveryData
          )
        )
    }
  }
}
