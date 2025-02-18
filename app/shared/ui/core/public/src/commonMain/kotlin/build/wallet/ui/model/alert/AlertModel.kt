package build.wallet.ui.model.alert

sealed interface AlertModel

/**
 * Alert dialog component with a title, subline, and one or two buttons.
 *
 * @property onDismiss called when customer requests to dismiss the alert other than using
 * the dialog's buttons - for example back button navigation or by tapping outside of the dialog.
 */
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

/**
 * Basic alert component with a text field. Only used by debug menu, not used in production.
 */
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
  primaryButtonText: String = "Disable",
  secondaryButtonText: String? = "Cancel",
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = ButtonAlertModel(
  title = title,
  subline = subline,
  primaryButtonText = primaryButtonText,
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = secondaryButtonText,
  onSecondaryButtonClick = onCancel,
  onDismiss = onCancel
)
