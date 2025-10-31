package build.wallet.ui.app.money.currency

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.AppearancePreferenceBodyModel
import build.wallet.statemachine.money.currency.AppearanceSection
import build.wallet.ui.app.core.form.CurrencyPreferenceListItemPickerMenu
import build.wallet.ui.model.render
import io.kotest.core.spec.style.FunSpec

class AppearancePreferenceFormSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Appearance preference display tab") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "Sats",
        bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onThemePreferenceClick = {},
        themePreferenceString = "System",
        defaultTimeScalePreferenceString = "1D",
        onDefaultTimeScalePreferenceClick = {},
        selectedSection = AppearanceSection.DISPLAY,
        onSectionSelected = {}
      ).render()
    }
  }
  test("Appearance preference currency tab") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "Sats",
        bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onThemePreferenceClick = {},
        themePreferenceString = "System",
        defaultTimeScalePreferenceString = "1D",
        onDefaultTimeScalePreferenceClick = {},
        selectedSection = AppearanceSection.CURRENCY,
        onSectionSelected = {}
      ).render()
    }
  }
  test("Appearance preference privacy tab") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "Sats",
        bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onThemePreferenceClick = {},
        themePreferenceString = "System",
        defaultTimeScalePreferenceString = "1D",
        onDefaultTimeScalePreferenceClick = {},
        selectedSection = AppearanceSection.PRIVACY,
        onSectionSelected = {}
      ).render()
    }
  }
})
