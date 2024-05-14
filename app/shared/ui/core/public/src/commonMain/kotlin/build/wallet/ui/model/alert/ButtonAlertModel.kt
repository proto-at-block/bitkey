package build.wallet.ui.model.alert

sealed interface AlertModel

data class ButtonAlertModel(
  val title: String,
  val subline: String?,
  val onDismiss: () -> Unit,
  val primaryButtonText: String,
  val onPrimaryButtonClick: () -> Unit,
  val primaryButtonStyle: ButtonStyle = ButtonStyle.Default,
  val secondaryButtonText: String? = null,
  val onSecondaryButtonClick: (() -> Unit)? = null,
  val secondaryButtonStyle: ButtonStyle = ButtonStyle.Default,
) : AlertModel {
  enum class ButtonStyle {
    Default,
    Destructive,
  }
}

data class InputAlertModel(
  val title: String,
  val subline: String?,
  val value: String,
  val onDismiss: () -> Unit,
  val onConfirm: (String) -> Unit,
  val onCancel: () -> Unit,
) : AlertModel

/**
 * An [ButtonAlertModel] with "Disable" and "Cancel" actions.
 *
 * @param onConfirm confirm to disable.
 * @param onCancel cancel confirmation dialog - action to not disable.
 */
fun DisableAlertModel(
  title: String,
  subline: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = ButtonAlertModel(
  title = title,
  subline = subline,
  primaryButtonText = "Disable",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onCancel,
  onDismiss = onCancel
)
