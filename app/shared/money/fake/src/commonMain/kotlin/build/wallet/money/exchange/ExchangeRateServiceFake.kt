package build.wallet.money.exchange

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock

class ExchangeRateServiceFake(clock: Clock = Clock.System) : ExchangeRateService {
  private val initialRates = listOf(
    ExchangeRate(
      fromCurrency = IsoCurrencyTextCode("BTC"),
      toCurrency = IsoCurrencyTextCode("USD"),
      rate = 33333.0,
      timeRetrieved = clock.now()
    )
  )

  override val exchangeRates = MutableStateFlow(initialRates)

  override suspend fun requestSync() {
    reset()
  }

  fun reset() {
    exchangeRates.value = initialRates
  }
}
