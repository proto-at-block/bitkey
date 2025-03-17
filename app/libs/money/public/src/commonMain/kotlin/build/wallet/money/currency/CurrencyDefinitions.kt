package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode

/**
 * This file contains hard-coded currency definitions.
 *
 * We hardcode Bitcoin for use in the app, but our fiat currency definitions are all server defined.
 * We only hardcode a few fiat currencies below to use as a fallback to ensure our app can function
 * in the case something goes wrong retrieving the server-backed fiat currency values.
 */

val BTC =
  CryptoCurrency(
    /**
     * Note: The correct ISO 4217 code for Bitcoin is not officially assigned by the ISO
     * organization because Bitcoin is a decentralized cryptocurrency and not a traditional fiat
     * currency. However, the commonly accepted code used in many contexts is BTC.
     *
     * Another common code is XBT, which adheres to the ISO 4217 standard for non-country-specific
     * currencies, though not officially assigned by the ISO organization either.
     *
     * We use BTC because this is what our backend uses.
     */
    textCode = IsoCurrencyTextCode("BTC"),
    unitSymbol = "₿",
    fractionalUnitConfiguration =
      CryptoCurrency.FractionalUnitConfiguration(
        name = "sat",
        namePlural = "sats",
        digits = 8
      )
  )

// Note: Only use these as a fallback. Fiat currency definitions should come from the server. //

val USD =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("USD"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "US Dollar",
        displayCountryCode = "US"
      ),
    unitSymbol = "$",
    fractionalDigits = 2
  )

val EUR =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("EUR"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Euro",
        displayCountryCode = "EU"
      ),
    unitSymbol = "€",
    fractionalDigits = 2
  )

val GBP =
  FiatCurrency(
    textCode = IsoCurrencyTextCode("GBP"),
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = "Pound Sterling",
        displayCountryCode = "GB"
      ),
    unitSymbol = "£",
    fractionalDigits = 2
  )
