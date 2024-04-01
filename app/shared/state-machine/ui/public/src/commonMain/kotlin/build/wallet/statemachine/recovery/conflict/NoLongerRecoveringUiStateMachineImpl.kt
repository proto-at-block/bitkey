package build.wallet.statemachine.recovery.conflict

import androidx.compose.runtime.Composable
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData
import build.wallet.statemachine.recovery.RecoverySegment
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
              errorData = null,
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
              errorData = null,
              onAcknowledge = {}
            ),
          presentationStyle = ScreenPresentationStyle.Modal
        )

      is NoLongerRecoveringData.ClearingLocalRecoveryFailedData ->
        ScreenModel(
          body =
            ShowingNoLongerRecoveringBodyModel(
              canceledRecoveringFactor = props.data.cancelingRecoveryLostFactor,
              errorData = ErrorData(
                segment = when (props.data.cancelingRecoveryLostFactor) {
                  App -> RecoverySegment.DelayAndNotify.LostApp.Cancellation
                  Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Cancellation
                },
                actionDescription = "Cancelling local recovery",
                cause = props.data.error
              ),
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
