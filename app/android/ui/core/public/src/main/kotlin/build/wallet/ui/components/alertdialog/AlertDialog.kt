package build.wallet.ui.components.alertdialog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
  MaterialAlertDialog(
    modifier = modifier,
    title = { Label(text = model.title, type = LabelType.Body1Medium) },
    text = { model.subline?.let { Label(text = it, type = LabelType.Body3Regular) } },
    containerColor = WalletTheme.colors.containerBackground,
    titleContentColor = WalletTheme.colors.foreground,
    textContentColor = WalletTheme.colors.foreground,
    onDismissRequest = { model.onDismiss.invoke() },
    confirmButton = {
      TextButton(modifier = Modifier.resId("confirm-alert"), onClick = {
        model.onPrimaryButtonClick.invoke()
      }) {
        Text(
          text = model.primaryButtonText.uppercase(),
          color = model.primaryButtonStyle.toComposeColor()
        )
      }
    },
    dismissButton = {
      model.secondaryButtonText?.let { secondaryButtonText ->
        TextButton(onClick = {
          model.onSecondaryButtonClick?.invoke()
        }) {
          Text(
            text = secondaryButtonText.uppercase(),
            color = model.secondaryButtonStyle.toComposeColor()
          )
        }
      }
    }
  )
}

@Composable
private fun AlertModel.ButtonStyle.toComposeColor(): Color =
  when (this) {
    AlertModel.ButtonStyle.Default -> Color.Unspecified
    AlertModel.ButtonStyle.Destructive -> WalletTheme.colors.destructive
  }

@Preview
@Composable
internal fun AlertDialogPreview() {
  PreviewWalletTheme {
    AlertDialog(
      AlertModel(
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
