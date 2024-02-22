package build.wallet.statemachine.recovery.inprogress.waiting

import build.wallet.ui.model.alert.AlertModel

fun CancelRecoveryAlertModel(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = AlertModel(
  title = "Are you sure you want to cancel?",
  subline = "Cancelling will undo any progress you’ve done so far.",
  onDismiss = onDismiss,
  primaryButtonText = "Cancel recovery",
  onPrimaryButtonClick = onConfirm,
  secondaryButtonText = "Nevermind",
  onSecondaryButtonClick = onDismiss
)
