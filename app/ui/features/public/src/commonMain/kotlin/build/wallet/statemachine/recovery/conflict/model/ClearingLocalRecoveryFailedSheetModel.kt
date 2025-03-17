package build.wallet.statemachine.recovery.conflict.model

import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext

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
      eventTrackerScreenId = null,
      renderContext = RenderContext.Sheet
    )
)
