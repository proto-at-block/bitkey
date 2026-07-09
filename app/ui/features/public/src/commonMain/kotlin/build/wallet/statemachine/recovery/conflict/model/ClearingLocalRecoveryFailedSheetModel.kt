package build.wallet.statemachine.recovery.conflict.model

import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.recovery.RecoverySegment

fun ClearingLocalRecoveryFailedSheetModel(
  onClose: () -> Unit,
  onRetry: () -> Unit,
) = SheetModel(
  onClosed = onClose,
  body =
    ErrorFormBodyModel(
      onBack = onClose,
      title = "We couldn’t clear the recovery",
      subline = "We are looking into this. Please try again later.",
      primaryButton =
        ButtonDataModel(
          text = "Retry",
          onClick = onRetry
        ),
      secondaryButton =
        ButtonDataModel(
          text = "Back",
          onClick = onClose
        ),
      errorData = ErrorData(
        segment = RecoverySegment.DelayAndNotify,
        actionDescription = "Clearing local recovery state",
        cause = IllegalStateException("Failed to clear local recovery state")
      ),
      eventTrackerScreenId = null,
      renderContext = RenderContext.Sheet
    )
)
