package build.wallet.statemachine.recovery.inprogress.waiting

import build.wallet.ui.model.alert.ButtonAlertModel

fun CancelRecoveryAlertModel(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = ButtonAlertModel(
  title = "Are you sure you want to cancel?",
  subline = "Cancelling will undo any progress you’ve done so far.",
  onDismiss = onDismiss,
  primaryButtonText = "Cancel recovery",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Nevermind",
  onSecondaryButtonClick = onDismiss
)
