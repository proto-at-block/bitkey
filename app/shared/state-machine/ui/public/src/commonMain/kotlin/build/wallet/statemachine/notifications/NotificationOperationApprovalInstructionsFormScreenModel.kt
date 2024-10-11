package build.wallet.statemachine.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun NotificationOperationApprovalInstructionsFormScreenModel(
  onExit: () -> Unit,
  operationDescription: String,
  onApprove: () -> Unit,
  isApproveButtonLoading: Boolean,
  errorBottomSheetState: ErrorBottomSheetState,
) = ScreenModel(
  body = NotificationOperationApprovalInstructionsBodyModel(
    onExit = onExit,
    operationDescription = operationDescription,
    onApprove = onApprove,
    isApproveButtonLoading = isApproveButtonLoading
  ),
  presentationStyle = ScreenPresentationStyle.Modal,
  bottomSheetModel =
    when (errorBottomSheetState) {
      is ErrorBottomSheetState.Hidden -> null
      is ErrorBottomSheetState.Showing ->
        SheetModel(
          onClosed = errorBottomSheetState.onClosed,
          body =
            ErrorFormBodyModel(
              title = "We were unable to continue with approval",
              subline =
                when {
                  errorBottomSheetState.isConnectivityError -> "Make sure you are connected to the internet and try again."
                  else -> "We are looking into this. Please try again later."
                },
              primaryButton =
                ButtonDataModel(
                  text = "Back",
                  onClick = errorBottomSheetState.onClosed
                ),
              renderContext = Sheet,
              eventTrackerScreenId = NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_ERROR_SHEET
            )
        )
    }
)

private data class NotificationOperationApprovalInstructionsBodyModel(
  val onExit: () -> Unit,
  val operationDescription: String,
  val onApprove: () -> Unit,
  val isApproveButtonLoading: Boolean,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL,
    onBack = onExit,
    toolbar =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onExit)
      ),
    header =
      FormHeaderModel(
        headline = "Approve this change with your Bitkey device",
        subline = operationDescription
      ),
    primaryButton =
      BitkeyInteractionButtonModel(
        text = "Approve",
        isLoading = isApproveButtonLoading,
        size = Footer,
        onClick = StandardClick(onApprove)
      )
  )
