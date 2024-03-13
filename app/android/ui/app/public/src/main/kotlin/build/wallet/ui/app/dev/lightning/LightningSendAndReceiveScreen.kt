package build.wallet.ui.app.dev.lightning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.dev.lightning.LightningSendReceiveBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun LightningSendAndReceiveScreen(model: LightningSendReceiveBodyModel) {
  val amountToReceive = remember { mutableStateOf(TextFieldValue(text = model.amountToReceive)) }
  val lightningInvoiceToFulfill =
    remember {
      mutableStateOf(
        TextFieldValue(text = model.invoiceInputValue)
      )
    }

  FormScreen(
    onBack = model.onBack,
    toolbarContent = {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = BackAccessory(onClick = model.onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(title = "Lightning Send/Receive")
          )
      )
    },
    mainContent = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(24.dp))
        Card {
          ListItem(
            title = "Lightning Balance",
            sideText = model.lightningBalance
          )
        }

        Spacer(Modifier.height(24.dp))
        Label(text = "Send", type = LabelType.Title3)
        Spacer(Modifier.height(24.dp))
        Divider()

        Spacer(Modifier.height(16.dp))
        Label(text = "Enter invoice to fulfill", type = LabelType.Body2Regular)
        Spacer(Modifier.height(16.dp))
        TextField(
          modifier = Modifier.fillMaxWidth(),
          placeholderText = "lnbc...",
          value = lightningInvoiceToFulfill.value,
          onValueChange = { newValue ->
            lightningInvoiceToFulfill.value = newValue
            model.onLightningInvoiceChanged(newValue.text)
          }
        )
        Spacer(Modifier.height(16.dp))
        Button(
          text = "Send",
          treatment = Primary,
          size = Footer,
          onClick = StandardClick(model.handleSendButtonPressed)
        )

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(24.dp))
        Label(text = "Receive", type = LabelType.Title3)
        Spacer(Modifier.height(24.dp))
        Divider()

        Spacer(Modifier.height(16.dp))
        Label(text = "Enter amount to receive (in msat)", type = LabelType.Body2Regular)
        Spacer(Modifier.height(16.dp))
        TextField(
          modifier = Modifier.fillMaxWidth(),
          placeholderText = "12345",
          value = amountToReceive.value,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          onValueChange = { newValue ->
            amountToReceive.value = newValue
            model.onAmountToReceiveChanged(newValue.text)
          }
        )
        Spacer(Modifier.height(16.dp))
        Button(
          text = "Generate Invoice",
          treatment = Primary,
          size = Footer,
          onClick = StandardClick(model.handleGenerateInvoicePressed)
        )

        model.generatedInvoiceString?.let {
          Spacer(Modifier.height(24.dp))
          Label(text = it, type = LabelType.Body2Regular)
        }
      }
    }
  )
}

@Preview
@Composable
internal fun SendAndReceiveScreenPreview() {
  PreviewWalletTheme {
    LightningSendAndReceiveScreen(
      LightningSendReceiveBodyModel(
        amountToReceive = "",
        invoiceInputValue = "",
        lightningBalance = "10000",
        generatedInvoiceString = null,
        onAmountToReceiveChanged = {},
        onLightningInvoiceChanged = {},
        handleSendButtonPressed = {},
        handleGenerateInvoicePressed = {},
        onBack = {}
      )
    )
  }
}
