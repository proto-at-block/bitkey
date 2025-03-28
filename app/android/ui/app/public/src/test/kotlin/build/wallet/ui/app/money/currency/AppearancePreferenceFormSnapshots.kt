package build.wallet.ui.app.money.currency

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.AppearancePreferenceFormModel
import build.wallet.ui.app.core.form.CurrencyPreferenceListItemPickerMenu
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AppearancePreferenceFormSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("appearance preference with back button") {
    paparazzi.snapshot {
      FormScreen(
        model = AppearancePreferenceFormModel(
          onBack = {},
          moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
          fiatCurrencyPreferenceString = "USD",
          onFiatCurrencyPreferenceClick = {},
          bitcoinDisplayPreferenceString = "Sats",
          bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
          onBitcoinDisplayPreferenceClick = {},
          onEnableHideBalanceChanged = {},
          isThemePreferenceEnabled = true,
          onThemePreferenceClick = {},
          themePreferenceString = "System",
          defaultTimeScalePreferenceString = "1D",
          onDefaultTimeScalePreferenceClick = {}
        )
      )
    }
  }
})
