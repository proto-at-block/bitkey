package build.wallet.money.display

import build.wallet.money.currency.USD

val CurrencyPreferenceDataMock =
  CurrencyPreferenceData(
    bitcoinDisplayUnitPreference = BitcoinDisplayUnit.Satoshi,
    setBitcoinDisplayUnitPreference = {},
    fiatCurrencyPreference = USD,
    setFiatCurrencyPreference = { _ -> }
  )
