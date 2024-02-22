package build.wallet.money.exchange

import build.wallet.money.currency.Currency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.datetime.Instant

/**
 * Describes an Exchange Rate, from one [Currency] to another based on their text code.
 * [ExchangeRate] is generally used with [CurrencyConverter].
 */
data class ExchangeRate(
  val fromCurrency: IsoCurrencyTextCode,
  val toCurrency: IsoCurrencyTextCode,
  /**
   * The rate to convert between [fromCurrency] to [toCurrency].
   */
  val rate: Double,
  val timeRetrieved: Instant,
)
