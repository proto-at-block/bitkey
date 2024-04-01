package build.wallet.ui.model.alert

data class AlertModel(
  val title: String,
  val subline: String?,
  val onDismiss: () -> Unit,
  val primaryButtonText: String,
  val onPrimaryButtonClick: () -> Unit,
  val primaryButtonStyle: ButtonStyle = ButtonStyle.Default,
  val secondaryButtonText: String? = null,
  val onSecondaryButtonClick: (() -> Unit)? = null,
  val secondaryButtonStyle: ButtonStyle = ButtonStyle.Default,
) {
  enum class ButtonStyle {
    Default,
    Destructive,
  }
}

/**
 * An [AlertModel] with "Disable" and "Cancel" actions.
 *
 * @param onConfirm confirm to disable.
 * @param onCancel cancel confirmation dialog - action to not disable.
 */
fun DisableAlertModel(
  title: String,
  subline: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = AlertModel(
  title = title,
  subline = subline,
  primaryButtonText = "Disable",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = AlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onCancel,
  onDismiss = onCancel
)
