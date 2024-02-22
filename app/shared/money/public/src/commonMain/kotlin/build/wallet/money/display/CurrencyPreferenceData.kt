package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency

/**
 * Data describing a customer's currency preferences for displaying bitcoin and fiat amounts.
 *
 * If no bitcoin display preference is explicitly set, it defaults to [Satoshis].
 *
 * If no fiat currency preference is explicitly set, it defaults to the currency corresponding
 * to the device's locale, if we support that currency, otherwise USD.
 */
data class CurrencyPreferenceData(
  val bitcoinDisplayUnitPreference: BitcoinDisplayUnit,
  val setBitcoinDisplayUnitPreference: (BitcoinDisplayUnit) -> Unit,
  val fiatCurrencyPreference: FiatCurrency,
  val setFiatCurrencyPreference: (FiatCurrency) -> Unit,
)
