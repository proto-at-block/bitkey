package build.wallet.ui.app.send

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferAmountBodyModel
import io.kotest.core.spec.style.FunSpec

class TransferAmountScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer amount entry screen - no entry") {
    paparazzi.snapshot {
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

  test("transfer amount entry screen - with entry") {
    paparazzi.snapshot {
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

  test("transfer amount entry screen - with banner") {
    paparazzi.snapshot {
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
})
