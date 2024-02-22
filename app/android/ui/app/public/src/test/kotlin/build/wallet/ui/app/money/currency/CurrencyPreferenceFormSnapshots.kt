package build.wallet.ui.app.money.currency

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.CurrencyPreferenceFormModel
import build.wallet.ui.app.core.form.CurrencyPreferenceListItemPickerMenu
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class CurrencyPreferenceFormSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("currency preference without back button") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CurrencyPreferenceFormModel(
            onBack = null,
            moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
            fiatCurrencyPreferenceString = "USD",
            onFiatCurrencyPreferenceClick = {},
            bitcoinDisplayPreferenceString = "Sats",
            bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
            onBitcoinDisplayPreferenceClick = {},
            onDone = {}
          )
      )
    }
  }

  test("currency preference with back button") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CurrencyPreferenceFormModel(
            onBack = {},
            moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
            fiatCurrencyPreferenceString = "USD",
            onFiatCurrencyPreferenceClick = {},
            bitcoinDisplayPreferenceString = "Sats",
            bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
            onBitcoinDisplayPreferenceClick = {},
            onDone = {}
          )
      )
    }
  }
})
