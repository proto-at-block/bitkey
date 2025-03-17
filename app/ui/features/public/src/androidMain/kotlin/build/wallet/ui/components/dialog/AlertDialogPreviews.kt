package build.wallet.ui.components.dialog

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.alertdialog.InputAlertDialog
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.InputAlertModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

private const val ALERT_TITLE = "Critical alerts"
private const val ALERT_SUBLINE =
  "Enabling push notifications for critical alerts is highly recommended to keep your funds safe."

@Preview
@Composable
fun AlertWithPrimaryAndSecondaryButtonsPreview() {
  PreviewWalletTheme(backgroundColor = WalletTheme.colors.mask) {
    AlertDialog(
      modifier = Modifier.width(300.dp),
      model = ButtonAlertModel(
        title = ALERT_TITLE,
        subline = ALERT_SUBLINE,
        onDismiss = {},
        onSecondaryButtonClick = {},
        onPrimaryButtonClick = {},
        primaryButtonText = "Confirm",
        secondaryButtonText = "Dismiss"
      )
    )
  }
}

@Preview
@Composable
fun AlertDialogWithPrimaryButtonOnlyPreview() {
  PreviewWalletTheme(backgroundColor = WalletTheme.colors.mask) {
    AlertDialog(
      modifier = Modifier.width(300.dp),
      model = ButtonAlertModel(
        title = ALERT_TITLE,
        subline = ALERT_SUBLINE,
        onDismiss = {},
        onPrimaryButtonClick = {},
        primaryButtonText = "Confirm"
      )
    )
  }
}

@Preview
@Composable
fun AlertDialogWithPrimaryDestructiveButtonPreview() {
  PreviewWalletTheme {
    AlertDialog(
      modifier = Modifier.width(300.dp),
      model = ButtonAlertModel(
        title = ALERT_TITLE,
        subline = ALERT_SUBLINE,
        onDismiss = {},
        onPrimaryButtonClick = {},
        primaryButtonText = "Confirm",
        primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
        secondaryButtonText = "Dismiss",
        onSecondaryButtonClick = {}
      )
    )
  }
}

@Preview
@Composable
fun AlertWithSecondaryDestructiveButtonPreview() {
  PreviewWalletTheme(backgroundColor = WalletTheme.colors.mask) {
    AlertDialog(
      modifier = Modifier.width(300.dp),
      model = ButtonAlertModel(
        title = ALERT_TITLE,
        subline = ALERT_SUBLINE,
        onDismiss = {},
        onSecondaryButtonClick = {},
        secondaryButtonText = "Dismiss",
        primaryButtonText = "Confirm",
        secondaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
        onPrimaryButtonClick = {}
      )
    )
  }
}

@Preview
@Composable
internal fun AlertWithTextFieldPreview() {
  PreviewWalletTheme {
    InputAlertDialog(
      model = InputAlertModel(
        title = "Alert Title",
        subline = "Alert Text",
        onDismiss = {},
        onConfirm = {},
        onCancel = {},
        value = "3000"
      )
    )
  }
}
