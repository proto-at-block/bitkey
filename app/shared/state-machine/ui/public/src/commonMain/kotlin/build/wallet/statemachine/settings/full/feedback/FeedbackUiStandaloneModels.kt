package build.wallet.statemachine.settings.full.feedback

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
}
