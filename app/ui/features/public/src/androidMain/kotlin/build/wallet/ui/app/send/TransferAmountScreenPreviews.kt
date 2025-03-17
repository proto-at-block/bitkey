package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.Color.ON60
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TransferAmountScreenNoEntryPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      model = TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          ),
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        cardModel = null,
        continueButtonEnabled = true,
        amountDisabled = false,
        onContinueClick = {},
        onSwapCurrencyClick = {}
      )
    )
  }
}

@Preview
@Composable
fun TransferAmountScreenWithEntryPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      model = TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        cardModel = null,
        continueButtonEnabled = true,
        amountDisabled = false,
        onContinueClick = {},
        onSwapCurrencyClick = {}
      )
    )
  }
}

@Preview
@Composable
fun TransferAmountScreenWithBannerPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      model = TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        cardModel = CardModel(
          title =
            LabelModel.StringWithStyledSubstringModel.from(
              string = "Bitkey approval required",
              substringToColor = emptyMap()
            ),
          subtitle = null,
          leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
          content = null,
          style = Outline
        ),
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        onContinueClick = {},
        onSwapCurrencyClick = {}
      )
    )
  }
}

@Preview
@Composable
fun TransferAmountScreenWithSmartBarPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      model = TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        cardModel = CardModel(
          title =
            LabelModel.StringWithStyledSubstringModel.from(
              string = "Bitkey approval required",
              substringToColor = emptyMap()
            ),
          subtitle = null,
          leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
          content = null,
          style = Outline
        ),
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        onContinueClick = {},
        onSwapCurrencyClick = {}
      )
    )
  }
}

@Preview
@Composable
fun TransferAmountScreenWithEqualOrMoreBannerPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      model = TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        cardModel = CardModel(
          title =
            LabelModel.StringWithStyledSubstringModel.from(
              string = "Send Max (balance minus fees)",
              substringToColor =
                mapOf(
                  "(balance minus fees)" to ON60
                )
            ),
          subtitle = null,
          leadingImage = null,
          content = null,
          style = Outline,
          onClick = {}
        ),
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = false,
        amountDisabled = true,
        onContinueClick = {},
        onSwapCurrencyClick = {}
      )
    )
  }
}
