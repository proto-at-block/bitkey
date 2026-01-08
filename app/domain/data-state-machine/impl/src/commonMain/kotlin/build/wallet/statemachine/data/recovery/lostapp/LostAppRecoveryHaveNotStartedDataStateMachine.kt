package build.wallet.statemachine.data.recovery.lostapp

import androidx.compose.runtime.*
import build.wallet.cloud.backup.CloudBackup
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryHaveNotStartedDataStateMachineImpl.State.AttemptingCloudBackupRecoveryState
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryHaveNotStartedDataStateMachineImpl.State.InitiatingLostAppRecoveryState
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryProps

interface LostAppRecoveryHaveNotStartedDataStateMachine :
  StateMachine<LostAppRecoveryHaveNotStartedProps, LostAppRecoveryHaveNotStartedData>

data class LostAppRecoveryHaveNotStartedProps(
  /**
   * List of cloud backups to try during recovery. The restoration flow will
   * attempt to decrypt each backup with the hardware key until one succeeds.
   */
  val cloudBackups: List<CloudBackup>,
  /**
   * Action to perform if recovery is rolled back.
   */
  val onRollback: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)

@BitkeyInject(AppScope::class)
class LostAppRecoveryHaveNotStartedDataStateMachineImpl(
  private val initiatingLostAppRecoveryDataStateMachine: InitiatingLostAppRecoveryDataStateMachine,
) : LostAppRecoveryHaveNotStartedDataStateMachine {
  @Composable
  override fun model(
    props: LostAppRecoveryHaveNotStartedProps,
  ): LostAppRecoveryHaveNotStartedData {
    var dataState: State by remember {
      mutableStateOf(
        if (props.cloudBackups.isEmpty()) {
          InitiatingLostAppRecoveryState
        } else {
          AttemptingCloudBackupRecoveryState(props.cloudBackups)
        }
      )
    }

    return dataState.let { state ->
      when (state) {
        is AttemptingCloudBackupRecoveryState ->
          AttemptingCloudRecoveryLostAppRecoveryDataData(
            cloudBackups = state.cloudBackups,
            rollback = props.onRollback,
            onRecoverAppKey = {
              dataState = InitiatingLostAppRecoveryState
            },
            goToLiteAccountCreation = props.goToLiteAccountCreation
          )

        is InitiatingLostAppRecoveryState ->
          initiatingLostAppRecoveryDataStateMachine.model(
            InitiatingLostAppRecoveryProps(
              onRollback = props.onRollback
            )
          )
      }
    }
  }

  private sealed interface State {
    data class AttemptingCloudBackupRecoveryState(
      val cloudBackups: List<CloudBackup>,
    ) : State

    /**
     * Indicates that we are initiating Lost App Delay & Notify recovery process.
     */
    data object InitiatingLostAppRecoveryState : State
  }
}
