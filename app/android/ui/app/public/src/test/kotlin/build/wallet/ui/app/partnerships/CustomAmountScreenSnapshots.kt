package build.wallet.ui.app.partnerships

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.ui.components.label.LabelTreatment
import io.kotest.core.spec.style.FunSpec

class CustomAmountScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.1)

  test("custom amount entry screen - invalid entry") {
    paparazzi.snapshot {
      CustomAmountScreen(
        model = CustomAmountBodyModel(
          onBack = {},
          limits = "From $20.00 to $100.00",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$5.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "Minimum buy amount is $20.00"
            ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
              onButtonPress = {}
            ),
          isAmountAboveMaximum = false,

          amountContextLineTreatment = LabelTreatment.Destructive,
          continueButtonEnabled = false,
          onNext = {}
        )
      )
    }
  }

  test("custom amount entry screen - above maximum") {
    paparazzi.snapshot {
      CustomAmountScreen(
        model = CustomAmountBodyModel(
          onBack = {},
          limits = "From $20.00 to $100.00",
          amountModel =
            MoneyAmountEntryModel(
              primaryAmount = "$150.00",
              primaryAmountGhostedSubstringRange = null,
              secondaryAmount = "Maximum buy amount is $100.00"
            ),
          keypadModel =
            KeypadModel(
              showDecimal = true,
              onButtonPress = {}
            ),
          isAmountAboveMaximum = true,

          amountContextLineTreatment = LabelTreatment.Destructive,
          continueButtonEnabled = false,
          onNext = {}
        )
      )
    }
  }

  test("custom amount entry screen - valid entry") {
    paparazzi.snapshot {
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

  test("custom amount entry screen - valid entry with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
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
})
