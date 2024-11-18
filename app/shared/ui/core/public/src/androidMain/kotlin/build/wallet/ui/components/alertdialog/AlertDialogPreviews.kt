package build.wallet.ui.components.alertdialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.InputAlertModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun ButtonAlertDialogPreview() {
  PreviewWalletTheme {
    AlertDialog(
      ButtonAlertModel(
        title = "Alert Title",
        subline = "Alert Text",
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
internal fun InputAlertDialogPreview() {
  PreviewWalletTheme {
    AlertDialog(
      InputAlertModel(
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
