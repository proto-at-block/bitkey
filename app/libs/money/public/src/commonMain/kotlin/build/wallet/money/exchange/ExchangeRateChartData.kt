package build.wallet.money.exchange

import build.wallet.money.currency.Currency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.datetime.Instant

/**
 * Describes exchange rate chart data, from one [Currency] to another based on their text code.
 */
data class ExchangeRateChartData(
  val fromCurrency: IsoCurrencyTextCode,
  val toCurrency: IsoCurrencyTextCode,
  val exchangeRates: List<PriceAt>,
)

data class PriceAt(val price: Double, val timestamp: Instant)
