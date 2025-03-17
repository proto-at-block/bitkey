package build.wallet.ui.app.partnerships

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import io.kotest.core.spec.style.FunSpec

class CustomAmountScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

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
          continueButtonEnabled = true,
          onNext = {}
        )
      )
    }
  }
})
