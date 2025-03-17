package build.wallet.statemachine.recovery.conflict

import androidx.compose.runtime.*
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.RecoveryDao
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachineImpl.State.*
import build.wallet.statemachine.recovery.conflict.model.ClearingLocalRecoveryFailedSheetModel
import build.wallet.statemachine.recovery.conflict.model.ShowingNoLongerRecoveringBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class NoLongerRecoveringUiStateMachineImpl(
  private val recoveryDao: RecoveryDao,
) : NoLongerRecoveringUiStateMachine {
  @Composable
  override fun model(props: NoLongerRecoveringUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingNoLongerRecovering) }
    return when (val currentState = state) {
      is ShowingNoLongerRecovering ->
        ScreenModel(
          body = ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = props.canceledRecoveryLostFactor,
            isLoading = false,
            errorData = null,
            onAcknowledge = {
              state = ClearingLocalRecovery
            }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )

      is ClearingLocalRecovery -> {
        LaunchedEffect("clear-local-recovery") {
          recoveryDao.clear()
            .onSuccess {
              // Nothing to do here. The state change will be handled by AccountDataStateMachine.
            }
            .onFailure {
              state = ClearingLocalRecoveryFailed(it)
            }
        }

        ScreenModel(
          body = ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = props.canceledRecoveryLostFactor,
            isLoading = true,
            errorData = null,
            onAcknowledge = {}
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ClearingLocalRecoveryFailed ->
        ScreenModel(
          body = ShowingNoLongerRecoveringBodyModel(
            canceledRecoveringFactor = props.canceledRecoveryLostFactor,
            errorData = ErrorData(
              segment = when (props.canceledRecoveryLostFactor) {
                App -> RecoverySegment.DelayAndNotify.LostApp.Cancellation
                Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Cancellation
              },
              actionDescription = "Cancelling local recovery",
              cause = currentState.error
            ),
            isLoading = false,
            onAcknowledge = {}
          ),
          presentationStyle = ScreenPresentationStyle.Modal,
          bottomSheetModel = ClearingLocalRecoveryFailedSheetModel(
            onClose = {
              state = ShowingNoLongerRecovering
            },
            onRetry = {
              state = ClearingLocalRecovery
            }
          )
        )
    }
  }

  private sealed interface State {
    /**
     * Indicates that we are showing the informative screen to the user explaining that
     * a recovery they initiated is no longer in progress because it was canceled elsewhere.
     */
    data object ShowingNoLongerRecovering : State

    /**
     * Indicates that we are in the process of clearing the locally persisted recovery.
     */
    data object ClearingLocalRecovery : State

    /**
     * Indicates that there was an issue when clearing the locally persisted recovery.
     */
    data class ClearingLocalRecoveryFailed(
      val error: Error,
    ) : State
  }
}
