package build.wallet.money.exchange

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

class ExchangeRateDaoFake : ExchangeRateDao {
  var historicalExchangeRates = MutableStateFlow(emptyMap<Instant, List<ExchangeRate>>())

  var allExchangeRates = MutableStateFlow<List<ExchangeRate>>(emptyList())

  override suspend fun storeExchangeRate(exchangeRate: ExchangeRate): Result<Unit, DbError> {
    // if the exchange rate for the same currency pair already exists, update it
    // otherwise, add it to the list
    allExchangeRates.update { currentRates ->
      val existingRate = currentRates.find {
        it.fromCurrency == exchangeRate.fromCurrency && it.toCurrency == exchangeRate.toCurrency
      }
      if (existingRate != null) {
        currentRates.map { if (it == existingRate) exchangeRate else it }
      } else {
        currentRates + exchangeRate
      }
    }
    return Ok(Unit)
  }

  override suspend fun storeHistoricalExchangeRate(
    exchangeRate: ExchangeRate,
    atTime: Instant,
  ): Result<Unit, DbError> {
    // if the exchange rate for the same currency pair and time already exists, update it
    // otherwise, add it to the list
    historicalExchangeRates.update { currentRates ->
      val existingRates = currentRates[atTime] ?: emptyList()
      val existingRate = existingRates.find {
        it.fromCurrency == exchangeRate.fromCurrency && it.toCurrency == exchangeRate.toCurrency && it.timeRetrieved == exchangeRate.timeRetrieved
      }
      if (existingRate != null) {
        currentRates + (atTime to existingRates.map { if (it == existingRate) exchangeRate else it })
      } else {
        currentRates + (atTime to (existingRates + exchangeRate))
      }
    }
    return Ok(Unit)
  }

  override fun allExchangeRates(): Flow<List<ExchangeRate>> = allExchangeRates

  override suspend fun historicalExchangeRatesAtTime(time: Instant): List<ExchangeRate>? =
    historicalExchangeRates.value[time]

  fun reset() {
    allExchangeRates.value = emptyList()
    historicalExchangeRates.value = emptyMap()
  }
}
