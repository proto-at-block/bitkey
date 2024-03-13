package build.wallet.statemachine.settings.full.feedback

import build.wallet.ui.model.alert.AlertModel

object FeedbackUiStandaloneModels {
  internal fun confirmLeaveAlertModel(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
  ) = AlertModel(
    title = "Are you sure you want to leave?",
    subline = "Leaving this screen will reset any information you provided.",
    onDismiss = onDismiss,
    primaryButtonText = "Leave",
    onPrimaryButtonClick = onConfirm,
    secondaryButtonText = "Stay",
    onSecondaryButtonClick = onDismiss
  )
}
