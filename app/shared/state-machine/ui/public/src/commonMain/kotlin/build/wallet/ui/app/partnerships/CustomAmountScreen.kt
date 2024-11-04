package build.wallet.ui.app.partnerships

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CustomAmountScreen(
  modifier: Modifier = Modifier,
  model: CustomAmountBodyModel,
) {
  val horizontalPadding = 20.dp
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    horizontalPadding = 0,
    toolbarContent = {
      Toolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbar
      )
    },
    mainContent = {
      Spacer(Modifier.weight(1F))
      HeroAmount(
        modifier =
          Modifier.align(CenterHorizontally)
            .padding(horizontal = 20.dp)
            .clipToBounds(),
        primaryAmount =
          buildAnnotatedString {
            append(model.amountModel.primaryAmount)
            model.amountModel.primaryAmountGhostedSubstringRange?.let { substringRange ->
              addStyle(
                style = SpanStyle(color = WalletTheme.colors.foreground30),
                start = substringRange.first,
                end = substringRange.last + 1
              )
            }
          },
        primaryAmountLabelType = LabelType.Display1,
        secondaryAmountWithCurrency = model.amountModel.secondaryAmount,
        onSwapClick = {},
        disabled = false
      )
      Spacer(Modifier.height(16.dp))

      Spacer(Modifier.weight(1F))

      Keypad(
        showDecimal = model.keypadModel.showDecimal,
        onButtonPress = model.keypadModel.onButtonPress
      )
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.primaryButton
      )
    }
  )
}

@Preview
@Composable
fun CustomAmountScreenInvalidEntryPreview() {
  PreviewWalletTheme {
    CustomAmountScreen(
      model = CustomAmountBodyModel(
        onBack = {},
        limits = "From $20.00 to $100.00",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$5.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = null
          ),
        keypadModel =
          KeypadModel(
            showDecimal = true,
            onButtonPress = {}
          ),
        continueButtonEnabled = false,
        onNext = {}
      )
    )
  }
}

@Preview
@Composable
fun CustomAmountScreenValidEntryPreview() {
  PreviewWalletTheme {
    CustomAmountScreen(
      model = CustomAmountBodyModel(
        onBack = {},
        limits = "From $20.00 to $100.00",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$50.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = null
          ),
        keypadModel =
          KeypadModel(
            showDecimal = true,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        onNext = {}
      )
    )
  }
}
