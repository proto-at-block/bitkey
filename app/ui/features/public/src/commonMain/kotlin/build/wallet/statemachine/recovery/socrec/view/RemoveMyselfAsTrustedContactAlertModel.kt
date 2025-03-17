package build.wallet.statemachine.recovery.socrec.view

import build.wallet.ui.model.alert.ButtonAlertModel

@Suppress("FunctionName")
fun RemoveMyselfAsTrustedContactAlertModel(
  alias: String,
  onDismiss: () -> Unit,
  onRemove: () -> Unit,
) = ButtonAlertModel(
  title = "Are you sure you want to remove yourself as a Trusted Contact?",
  subline = "If $alias needs help recovering in the future, you won’t be able to assist them.",
  onDismiss = onDismiss,
  primaryButtonText = "Remove",
  onPrimaryButtonClick = onRemove,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onDismiss
)
