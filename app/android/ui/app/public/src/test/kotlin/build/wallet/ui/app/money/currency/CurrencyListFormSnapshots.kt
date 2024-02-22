package build.wallet.ui.app.money.currency

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.statemachine.money.currency.FiatCurrencyListFormModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class CurrencyListFormSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("currency list") {
    paparazzi.snapshot {
      FormScreen(
        model =
          FiatCurrencyListFormModel(
            onClose = {},
            selectedCurrency = USD,
            currencyList = listOf(USD, EUR, GBP),
            onCurrencySelection = {}
          )
      )
    }
  }
})
