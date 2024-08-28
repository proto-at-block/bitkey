package build.wallet.ui.components.alertdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.compose.resId
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.InputAlertModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.BasicAlertDialog as MaterialBasicAlertDialog

@Composable
fun AlertDialog(
  model: AlertModel,
  modifier: Modifier = Modifier,
) {
  when (model) {
    is ButtonAlertModel -> ButtonAlertDialog(model, modifier)
    is InputAlertModel -> InputAlertDialog(modifier, model)
  }
}

@Composable
fun ButtonAlertDialog(
  model: ButtonAlertModel,
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
fun InputAlertDialog(
  modifier: Modifier = Modifier,
  model: InputAlertModel,
) {
  var inputValue by remember {
    mutableStateOf(model.value)
  }

  MaterialBasicAlertDialog(
    modifier = modifier
      .clip(AlertDialogDefaults.shape)
      .background(WalletTheme.colors.containerBackground)
      .padding(24.dp),
    onDismissRequest = { model.onDismiss.invoke() }
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
    ) {
      Label(text = model.title, type = LabelType.Body1Medium)
      model.subline?.let { Label(text = it, type = LabelType.Body3Regular) }
      Spacer(modifier = Modifier.height(16.dp))
      TextField(
        model = TextFieldModel(
          value = inputValue,
          onValueChange = { s, _ -> inputValue = s },
          placeholderText = "",
          keyboardType = TextFieldModel.KeyboardType.Decimal,
          onDone = {}
        ),
        labelType = LabelType.Body2Regular,
        trailingButtonModel = null
      )
      Spacer(modifier = Modifier.height(16.dp))
      Row(modifier = Modifier.align(Alignment.End)) {
        TextButton(modifier = Modifier.resId("dismiss-alert"), onClick = {
          model.onCancel()
        }) {
          Text(
            text = "Cancel".uppercase()
          )
        }
        TextButton(modifier = Modifier.resId("confirm-alert"), onClick = {
          model.onConfirm(inputValue)
        }) {
          Text(
            text = "Confirm".uppercase()
          )
        }
      }
    }
  }
}

@Composable
private fun ButtonAlertModel.ButtonStyle.toComposeColor(): Color =
  when (this) {
    ButtonAlertModel.ButtonStyle.Default -> Color.Unspecified
    ButtonAlertModel.ButtonStyle.Destructive -> WalletTheme.colors.destructive
  }

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
