package build.wallet.statemachine.recovery.conflict

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData
import build.wallet.statemachine.recovery.conflict.model.ClearingLocalRecoveryFailedSheetModel
import build.wallet.statemachine.recovery.conflict.model.ShowingNoLongerRecoveringBodyModel

class NoLongerRecoveringUiStateMachineImpl : NoLongerRecoveringUiStateMachine {
  @Composable
  override fun model(props: NoLongerRecoveringUiProps): ScreenModel {
    return when (props.data) {
      is NoLongerRecoveringData.ShowingNoLongerRecoveringData ->
        ScreenModel(
          body =
            ShowingNoLongerRecoveringBodyModel(
              canceledRecoveringFactor = props.data.canceledRecoveryLostFactor,
              isLoading = props.data is NoLongerRecoveringData.ClearingLocalRecoveryData,
              onAcknowledge = props.data.onAcknowledge
            ),
          presentationStyle = ScreenPresentationStyle.Modal
        )

      is NoLongerRecoveringData.ClearingLocalRecoveryData ->
        ScreenModel(
          body =
            ShowingNoLongerRecoveringBodyModel(
              canceledRecoveringFactor = props.data.cancelingRecoveryLostFactor,
              isLoading = true,
              onAcknowledge = {}
            ),
          presentationStyle = ScreenPresentationStyle.Modal
        )

      is NoLongerRecoveringData.ClearingLocalRecoveryFailedData ->
        ScreenModel(
          body =
            ShowingNoLongerRecoveringBodyModel(
              canceledRecoveringFactor = props.data.cancelingRecoveryLostFactor,
              isLoading = false,
              onAcknowledge = {}
            ),
          presentationStyle = ScreenPresentationStyle.Modal,
          bottomSheetModel =
            ClearingLocalRecoveryFailedSheetModel(
              onClose = props.data.rollback,
              onRetry = props.data.retry
            )
        )
    }
  }
}
