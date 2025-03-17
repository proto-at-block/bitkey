package build.wallet.ui.components.alertdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.resId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.ButtonAlertModel.ButtonStyle
import build.wallet.ui.model.alert.InputAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Title2
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
  Dialog(
    onDismissRequest = model.onDismiss,
    properties = DialogProperties(
      // We use custom width and insets.
      usePlatformDefaultWidth = true
    ),
    content = {
      ButtonAlertDialogContent(model, modifier)
    }
  )
}

@Composable
private fun ButtonAlertDialogContent(
  model: ButtonAlertModel,
  modifier: Modifier = Modifier,
) {
  Card(modifier) {
    Spacer(modifier = Modifier.height(16.dp))
    Label(text = model.title, type = Title2)
    model.subline?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Label(text = model.subline, treatment = LabelTreatment.Secondary)
    }
    Spacer(modifier = Modifier.height(12.dp))
    RowOfButtons(
      buttonContents = ButtonContentsList(
        buttonContents = listOfNotNull(
          model.secondaryButtonText?.let {
            {
              Button(
                modifier = Modifier.weight(1F),
                model = ButtonModel(
                  text = model.secondaryButtonText,
                  treatment = when (model.secondaryButtonStyle) {
                    ButtonStyle.Default -> ButtonModel.Treatment.Secondary
                    ButtonStyle.Destructive -> ButtonModel.Treatment.SecondaryDestructive
                  },
                  size = ButtonModel.Size.Short,
                  onClick = StandardClick { model.onSecondaryButtonClick?.invoke() }
                )
              )
            }
          },
          {
            Button(
              modifier = Modifier.weight(1F),
              model = ButtonModel(
                text = model.primaryButtonText,
                treatment = when (model.primaryButtonStyle) {
                  ButtonStyle.Default -> ButtonModel.Treatment.Primary
                  ButtonStyle.Destructive -> ButtonModel.Treatment.PrimaryDestructive
                },
                size = ButtonModel.Size.Short,
                onClick = StandardClick(model.onPrimaryButtonClick)
              )
            )
          }
        )
      ),
      interButtonSpacing = 16.dp
    )
    Spacer(modifier = Modifier.height(16.dp))
  }
}

/**
 * This component is not designed for production use. It's only used in the debug menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
