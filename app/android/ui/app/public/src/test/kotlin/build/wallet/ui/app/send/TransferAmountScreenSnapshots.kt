package build.wallet.ui.app.send

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.Color.ON60
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.statemachine.send.amountentry.SmartBarModel
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
          smartBarModel = null,
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
          smartBarModel = null,
          continueButtonEnabled = true,
          amountDisabled = false,
          onContinueClick = {},
          onSwapCurrencyClick = {}
        )
      )
    }
  }

  test("transfer amount entry screen - with smart bar") {
    paparazzi.snapshot {
      TransferAmountScreen(
        model = TransferAmountBodyModel(
          onBack = {},
          balanceTitle = "$961.24 available",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$961.24",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "1,672,500 sats"
            ),
          smartBarModel = SmartBarModel(
            title = LabelModel.StringWithStyledSubstringModel.from(
              string = "Send Max (balance minus fees)",
              substringToColor = mapOf("(balance minus fees)" to ON60)
            ),
            onClick = {}
          ),
          keypadModel =
            KeypadModel(
              showDecimal = false,
              onButtonPress = {}
            ),
          continueButtonEnabled = false,
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
          smartBarModel = null,
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
          smartBarModel = null,
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
          smartBarModel = null,
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
})
