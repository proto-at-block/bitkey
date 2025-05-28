package build.wallet.statemachine.data.recovery.lostapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
   * Cloud backup data, if any, used to determine recovery options.
   */
  val cloudBackup: CloudBackup?,
  /**
   * Action to perform if recovery is rolled back.
   */
  val onRollback: () -> Unit,
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
        if (props.cloudBackup == null) {
          InitiatingLostAppRecoveryState
        } else {
          AttemptingCloudBackupRecoveryState(props.cloudBackup)
        }
      )
    }

    return dataState.let { state ->
      when (state) {
        is AttemptingCloudBackupRecoveryState ->
          AttemptingCloudRecoveryLostAppRecoveryDataData(
            cloudBackup = state.cloudBackup,
            rollback = props.onRollback,
            onRecoverAppKey = {
              dataState = InitiatingLostAppRecoveryState
            }
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
      val cloudBackup: CloudBackup,
    ) : State

    /**
     * Indicates that we are initiating Lost App Delay & Notify recovery process.
     */
    data object InitiatingLostAppRecoveryState : State
  }
}
