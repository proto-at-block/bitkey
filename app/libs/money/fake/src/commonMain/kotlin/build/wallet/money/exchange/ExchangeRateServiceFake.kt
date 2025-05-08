package build.wallet.money.exchange

import build.wallet.money.currency.Currency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

class ExchangeRateServiceFake(private val clock: Clock = Clock.System) : ExchangeRateService {
  private val initialRates = listOf(
    ExchangeRate(
      fromCurrency = IsoCurrencyTextCode("BTC"),
      toCurrency = IsoCurrencyTextCode("USD"),
      rate = 33333.0,
      timeRetrieved = clock.now()
    )
  )

  override val exchangeRates = MutableStateFlow(initialRates)

  override fun mostRecentRatesSinceDurationForCurrency(
    duration: Duration,
    currency: Currency,
  ): List<ExchangeRate>? {
    val instant = exchangeRates.value.timeRetrievedForCurrency(currency)
    return when {
      // if rates are older than duration or we cant find any for our fiat currency, we don't
      // use them
      instant == null || instant <= clock.now() - duration -> null
      else -> exchangeRates.value
    }
  }

  fun reset() {
    exchangeRates.value = initialRates
  }

  private fun List<ExchangeRate>.timeRetrievedForCurrency(currency: Currency): Instant? {
    return firstOrNull { it.fromCurrency == currency.textCode || it.toCurrency == currency.textCode }
      ?.timeRetrieved
  }
}
