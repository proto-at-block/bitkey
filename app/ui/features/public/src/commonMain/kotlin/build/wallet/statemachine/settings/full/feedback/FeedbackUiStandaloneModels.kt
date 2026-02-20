package build.wallet.statemachine.settings.full.feedback

import build.wallet.support.MAX_MEDIA_ATTACHMENTS
import build.wallet.ui.model.alert.ButtonAlertModel

object FeedbackUiStandaloneModels {
  internal fun confirmLeaveAlertModel(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
  ) = ButtonAlertModel(
    title = "Are you sure you want to leave?",
    subline = "Leaving this screen will reset any information you provided.",
    onDismiss = onDismiss,
    primaryButtonText = "Leave",
    onPrimaryButtonClick = onConfirm,
    primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
    secondaryButtonText = "Stay",
    onSecondaryButtonClick = onDismiss
  )

  internal fun attachmentLimitExceededAlertModel(
    attemptedCount: Int,
    addedCount: Int,
    onDismiss: () -> Unit,
  ) = ButtonAlertModel(
    title = "Attachment limit reached",
    subline = "You can attach up to $MAX_MEDIA_ATTACHMENTS files. " + if (addedCount > 1) {
      "Only $addedCount of your $attemptedCount selected files were added."
    } else if (addedCount > 0) {
      "Only $addedCount of your $attemptedCount selected files was added."
    } else {
      "You've already reached the limit."
    },
    onDismiss = onDismiss,
    primaryButtonText = "OK",
    onPrimaryButtonClick = onDismiss
  )
}
