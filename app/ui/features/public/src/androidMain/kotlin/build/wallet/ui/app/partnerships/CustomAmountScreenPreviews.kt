package build.wallet.ui.app.partnerships

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.ui.tokens.lightStyleDictionaryColors
import build.wallet.ui.tooling.PreviewWalletTheme

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
        isAmountAboveMaximum = false,
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
        isAmountAboveMaximum = false,

        continueButtonEnabled = true,
        onNext = {}
      )
    )
  }
}

@Preview
@Composable
fun CustomAmountScreenValidEntryDesignSystemV2Preview() {
  PreviewWalletTheme(
    designSystemUpdatesEnabled = true,
    backgroundColor = lightStyleDictionaryColors.subtleBackground
  ) {
    CustomAmountScreen(
      model = CustomAmountBodyModel(
        onBack = {},
        limits = "From $20.00 to $100.00",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$50.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "50,000 sats"
          ),
        keypadModel =
          KeypadModel(
            showDecimal = true,
            onButtonPress = {}
          ),
        isAmountAboveMaximum = false,

        continueButtonEnabled = true,
        onNext = {}
      )
    )
  }
}
