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
      mutableStateOf(ShowingNoLongerRecoveringDataState)
    }

    return when (val state = dataState) {
      is ShowingNoLongerRecoveringDataState -> {
        NoLongerRecoveringData.ShowingNoLongerRecoveringData(
          canceledRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          onAcknowledge = {
            dataState = ClearingLocalRecoveryDataState
          }
        )
      }

      is ClearingLocalRecoveryDataState -> {
        LaunchedEffect("clear-local-recovery") {
          recoveryDao
            .clear()
            .onSuccess {
              // Nothing to do
            }
            .onFailure {
              dataState = ClearingLocalRecoveryFailedDataState(it)
            }
        }
        NoLongerRecoveringData.ClearingLocalRecoveryData(
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor
        )
      }

      is ClearingLocalRecoveryFailedDataState ->
        NoLongerRecoveringData.ClearingLocalRecoveryFailedData(
          error = state.error,
          cancelingRecoveryLostFactor = props.cancelingRecoveryLostFactor,
          rollback = {
            dataState = ShowingNoLongerRecoveringDataState
          },
          retry = {
            dataState = ClearingLocalRecoveryDataState
          }
        )
    }
  }

  private sealed interface State {
    data object ShowingNoLongerRecoveringDataState : State

    data object ClearingLocalRecoveryDataState : State

    data class ClearingLocalRecoveryFailedDataState(
      val error: Error,
    ) : State
  }
}
