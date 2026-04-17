package build.wallet.statemachine.walletmigration

import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * Confirmation alert shown when the user wants to leave the W3 upgrade flow
 * because they don't have their old (W1) Bitkey device available.
 */
fun w3UpgradeExitConfirmationAlertModel(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = ButtonAlertModel(
  title = "Leave upgrade for now?",
  subline = "You can start the upgrade again when you have your old Bitkey available.",
  primaryButtonText = "Leave",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onDismiss,
  onDismiss = onDismiss
)
