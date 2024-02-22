package build.wallet.money

import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode

// Fiat definitions that are for testing purposes //

val testAUD =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("AUD"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Australian Dollar",
        displayCountryCode = "AU"
      ),
    unitSymbol = "$",
    fractionalDigits = 2
  )

val testCAD =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("CAD"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Canadian Dollar",
        displayCountryCode = "CA"
      ),
    unitSymbol = "$",
    fractionalDigits = 2
  )

val testJPY =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("JPY"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Yen",
        displayCountryCode = "JP"
      ),
    unitSymbol = "¥",
    fractionalDigits = 0
  )

val testKWD =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("KWD"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Kuwaiti Dinar",
        displayCountryCode = "KW"
      ),
    unitSymbol = "KWD",
    fractionalDigits = 3
  )

val allTestingFiatCurrencies =
  listOf(
    testAUD,
    testCAD,
    EUR,
    GBP,
    testKWD,
    testJPY,
    USD
  )
