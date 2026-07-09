package build.wallet.ui.app.send

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon.Bitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.ui.components.label.LabelTreatment
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
            leadingImage = CardModel.CardImage.StaticImage(Bitkey),
            content = null,
            style = Outline()
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

  test("sell amount entry screen - below minimum") {
    paparazzi.snapshot {
      TransferAmountScreen(
        model = TransferAmountBodyModel(
          onBack = {},
          balanceTitle = "$50.00 available",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$5.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "Minimum sell amount is $14.99"
            ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
              onButtonPress = {}
            ),
          cardModel = null,
          continueButtonEnabled = false,
          amountDisabled = false,
          amountContextLineTreatment = LabelTreatment.Destructive,
          onContinueClick = {},
          onSwapCurrencyClick = null
        )
      )
    }
  }

  test("sell amount entry screen - above maximum") {
    paparazzi.snapshot {
      TransferAmountScreen(
        model = TransferAmountBodyModel(
          onBack = {},
          balanceTitle = "$50,000.00 available",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$40,000.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "Maximum sell amount is $35,680.00"
            ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
              onButtonPress = {}
            ),
          cardModel = null,
          continueButtonEnabled = false,
          amountDisabled = false,
          amountContextLineTreatment = LabelTreatment.Destructive,
          onContinueClick = {},
          onSwapCurrencyClick = null
        )
      )
    }
  }

  test("sell amount entry screen - exceeds balance") {
    paparazzi.snapshot {
      TransferAmountScreen(
        model = TransferAmountBodyModel(
          onBack = {},
          balanceTitle = "$50.00 available",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$75.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "Amount exceeds available balance"
            ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
              onButtonPress = {}
            ),
          cardModel = null,
          continueButtonEnabled = false,
          amountDisabled = false,
          amountContextLineTreatment = LabelTreatment.Destructive,
          shouldTriggerContextualErrorFeedback = true,
          onContinueClick = {},
          onSwapCurrencyClick = null
        )
      )
    }
  }

  test("transfer amount entry screen - insufficient funds") {
    paparazzi.snapshot {
      TransferAmountScreen(
        model = TransferAmountBodyModel(
          onBack = {},
          balanceTitle = "$961.24 available",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$1,500.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "2,625,000 sats"
            ),
          cardModel = CardModel(
            title =
              LabelModel.StringWithStyledSubstringModel.from(
                string = "You don't have enough available",
                substringToColor = emptyMap()
              ),
            subtitle = null,
            leadingImage = null,
            content = null,
            style = Outline(),
            titleTreatment = CardModel.TitleTreatment.Destructive
          ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
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
})
