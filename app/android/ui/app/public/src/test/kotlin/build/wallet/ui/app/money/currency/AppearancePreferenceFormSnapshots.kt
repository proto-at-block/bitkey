package build.wallet.ui.app.money.currency

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.money.currency.AppearancePreferenceBodyModel
import build.wallet.statemachine.money.currency.AppearanceSection
import build.wallet.statemachine.money.currency.MoneyHomeHeroModel
import build.wallet.ui.model.render
import io.kotest.core.spec.style.FunSpec

class AppearancePreferenceFormSnapshots : FunSpec({
  // light-mode appearance snapshots have a small, repeatable Linux CI rendering drift
  // beyond the shared default threshold.
  val paparazzi = paparazziExtension(maxPercentDifference = 0.05)

  test("Appearance preference display tab") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = MoneyHomeHeroModel(
          primaryAmount = "$0",
          secondaryAmount = "0 sats",
          isHidden = false,
          isPriceGraphEnabled = false,
          selectedSection = AppearanceSection.DISPLAY
        ),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "sats",
        isBitcoinPriceCardEnabled = false,
        isHideBalanceEnabled = false,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onBitcoinPriceCardPreferenceClick = {},
        onThemePreferenceClick = {},
        themePreferenceString = "System",
        defaultTimeScalePreferenceString = "1D",
        onDefaultTimeScalePreferenceClick = {},
        selectedSection = AppearanceSection.DISPLAY,
        onSectionSelected = {}
      ).render()
    }
  }

  test("Appearance preference currency tab BIP 177") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = MoneyHomeHeroModel(
          primaryAmount = "$0",
          secondaryAmount = "₿0",
          isHidden = false,
          isPriceGraphEnabled = true,
          selectedSection = AppearanceSection.CURRENCY
        ),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "₿",
        isBitcoinPriceCardEnabled = false,
        isHideBalanceEnabled = false,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onBitcoinPriceCardPreferenceClick = {},
        onThemePreferenceClick = {},
        themePreferenceString = "System",
        defaultTimeScalePreferenceString = "1D",
        onDefaultTimeScalePreferenceClick = {},
        selectedSection = AppearanceSection.CURRENCY,
        onSectionSelected = {}
      ).render()
    }
  }

  test("Appearance preference currency tab") {
    paparazzi.snapshot {
      AppearancePreferenceBodyModel(
        onBack = {},
        moneyHomeHero = MoneyHomeHeroModel(
          primaryAmount = "$0",
          secondaryAmount = "0 sats",
          isHidden = false,
          isPriceGraphEnabled = false,
          selectedSection = AppearanceSection.CURRENCY
        ),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "sats",
        isBitcoinPriceCardEnabled = false,
        isHideBalanceEnabled = false,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onBitcoinPriceCardPreferenceClick = {},
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
        moneyHomeHero = MoneyHomeHeroModel(
          primaryAmount = "$0",
          secondaryAmount = "0 sats",
          isHidden = false,
          isPriceGraphEnabled = false,
          selectedSection = AppearanceSection.PRIVACY
        ),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "sats",
        isBitcoinPriceCardEnabled = false,
        isHideBalanceEnabled = false,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {},
        onBitcoinPriceCardPreferenceClick = {},
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
