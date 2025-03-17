package build.wallet.statemachine.recovery.inprogress.waiting

import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.ButtonAlertModel.ButtonStyle.Destructive

fun cancelRecoveryAlertModel(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = ButtonAlertModel(
  title = "Are you sure you want to cancel recovery?",
  subline = "Cancelling will undo any progress you’ve done so far.",
  primaryButtonText = "Cancel",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = Destructive,
  secondaryButtonText = "Nevermind",
  onSecondaryButtonClick = onDismiss,
  onDismiss = onDismiss
)
