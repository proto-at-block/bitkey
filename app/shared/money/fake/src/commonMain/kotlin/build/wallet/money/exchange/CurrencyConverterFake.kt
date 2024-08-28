package build.wallet.money.exchange

import build.wallet.money.Money
import build.wallet.money.currency.Currency
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

/**
 * Fake [CurrencyConverter] that converts money amounts according to
 * provided conversion rates.
 */
class CurrencyConverterFake(
  var conversionRate: Double? = 3.0,
  var historicalConversionRate: Map<Instant, Double> = emptyMap(),
) : CurrencyConverter {
  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    rates: List<ExchangeRate>,
  ): Money? {
    return conversionRate?.let {
      Money.money(
        currency = toCurrency,
        value = fromAmount.value.multiply(it.toBigDecimal())
      )
    }
  }

  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    atTime: Instant?,
  ): Flow<Money?> {
    // If the caller didn't give a specific time, just return the flow of current rates
    // This matches actual implementation
    if (atTime == null) {
      return flowOf(convert(fromAmount, toCurrency, rates = emptyList()))
    }

    if (historicalConversionRate[atTime] == null) {
      return flowOf(convert(fromAmount, toCurrency, rates = emptyList()))
    }

    return flowOf(
      Money.money(
        currency = toCurrency,
        value = fromAmount.value.multiply(historicalConversionRate[atTime]!!.toBigDecimal())
      )
    )
  }

  override fun latestRateTimestamp(
    fromCurrency: Currency,
    toCurrency: Currency,
  ): Flow<Instant?> {
    return flowOf(historicalConversionRate.maxOfOrNull { it.key })
  }

  fun reset() {
    conversionRate = 3.0
    historicalConversionRate = emptyMap()
  }
}
