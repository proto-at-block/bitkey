package build.wallet.statemachine.data.recovery.conflict

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.recovery.RecoveryDao
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachineImpl.State.ClearingLocalRecoveryDataState
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachineImpl.State.ClearingLocalRecoveryFailedDataState
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachineImpl.State.ShowingNoLongerRecoveringDataState
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class NoLongerRecoveringDataStateMachineImpl(
  private val recoveryDao: RecoveryDao,
) : NoLongerRecoveringDataStateMachine {
  @Composable
  override fun model(props: NoLongerRecoveringDataStateMachineDataProps): NoLongerRecoveringData {
    var dataState: State by remember {
      mutableStateOf(State.ShowingNoLongerRecoveringDataState)
    }

    return when (dataState) {
      is State.ShowingNoLongerRecoveringDataState -> {
        NoLongerRecoveringData.ShowingNoLongerRecoveringData(
          canceledRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          onAcknowledge = {
            dataState = State.ClearingLocalRecoveryDataState
          }
        )
      }

      is State.ClearingLocalRecoveryDataState -> {
        LaunchedEffect("clear-local-recovery") {
          recoveryDao
            .clear()
            .onSuccess {
              // Nothing to do
            }
            .onFailure {
              dataState = State.ClearingLocalRecoveryFailedDataState
            }
        }
        NoLongerRecoveringData.ClearingLocalRecoveryData(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor
        )
      }

      is State.ClearingLocalRecoveryFailedDataState ->
        NoLongerRecoveringData.ClearingLocalRecoveryFailedData(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          rollback = {
            dataState = State.ShowingNoLongerRecoveringDataState
          },
          retry = {
            dataState = State.ClearingLocalRecoveryDataState
          }
        )
    }
  }

  private sealed interface State {
    data object ShowingNoLongerRecoveringDataState : State

    data object ClearingLocalRecoveryDataState : State

    data object ClearingLocalRecoveryFailedDataState : State
  }
}
