package build.wallet.ui.components.alertdialog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.components.label.Label
import build.wallet.ui.compose.resId
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import androidx.compose.material3.AlertDialog as MaterialAlertDialog

@Composable
fun AlertDialog(
  model: AlertModel,
  modifier: Modifier = Modifier,
) {
  AlertDialog(
    modifier = modifier,
    title = model.title,
    subline = model.subline,
    onPrimaryButtonClick = model.onPrimaryButtonClick,
    primaryButtonText = model.primaryButtonText.uppercase(),
    onDismiss = model.onDismiss,
    onSecondaryButtonClick = model.onSecondaryButtonClick,
    secondaryButtonText = model.secondaryButtonText?.uppercase()
  )
}

@Composable
fun AlertDialog(
  modifier: Modifier = Modifier,
  title: String,
  subline: String?,
  onDismiss: (() -> Unit)? = null,
  onSecondaryButtonClick: (() -> Unit)? = null,
  onPrimaryButtonClick: (() -> Unit)? = null,
  primaryButtonText: String,
  secondaryButtonText: String? = null,
) {
  MaterialAlertDialog(
    modifier = modifier,
    title = { Label(text = title, type = LabelType.Body1Medium) },
    text = { subline?.let { Label(text = it, type = LabelType.Body3Regular) } },
    containerColor = WalletTheme.colors.containerBackground,
    titleContentColor = WalletTheme.colors.foreground,
    textContentColor = WalletTheme.colors.foreground,
    onDismissRequest = { onDismiss?.invoke() },
    confirmButton = {
      TextButton(modifier = Modifier.resId("confirm-alert"), onClick = {
        onPrimaryButtonClick?.invoke()
      }) {
        Text(
          text = primaryButtonText,
          color = WalletTheme.colors.destructive
        )
      }
    },
    dismissButton = {
      secondaryButtonText?.let {
        TextButton(onClick = {
          onSecondaryButtonClick?.invoke()
        }) {
          Text(text = secondaryButtonText)
        }
      }
    }
  )
}

@Preview
@Composable
internal fun AlertDialogPreview() {
  PreviewWalletTheme {
    AlertDialog(
      title = "Alert Title",
      subline = "Alert Text",
      onDismiss = {},
      onSecondaryButtonClick = {},
      onPrimaryButtonClick = {},
      primaryButtonText = "Confirm",
      secondaryButtonText = "Dismiss"
    )
  }
}
