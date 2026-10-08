package build.wallet.money.exchange

import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

class ExchangeRateDaoFake : ExchangeRateDao {
  var historicalExchangeRates = MutableStateFlow(emptyMap<Instant, List<ExchangeRate>>())

  var allExchangeRates = MutableStateFlow<List<ExchangeRate>>(emptyList())

  /** Provenance for each cached rate, keyed by currency pair. */
  var rateEnvironments = MutableStateFlow<Map<Pair<String, String>, F8eEnvironment?>>(emptyMap())

  private fun ExchangeRate.key() = fromCurrency.code to toCurrency.code

  override suspend fun storeExchangeRate(
    exchangeRate: ExchangeRate,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    rateEnvironments.update { it + (exchangeRate.key() to f8eEnvironment) }
    return storeExchangeRate(exchangeRate)
  }

  override suspend fun deleteRatesNotFrom(
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error> {
    val keep = rateEnvironments.value.filterValues { it == f8eEnvironment }.keys
    allExchangeRates.update { rates -> rates.filter { it.key() in keep } }
    rateEnvironments.update { it.filterKeys { key -> key in keep } }
    return Ok(Unit)
  }

  override fun allExchangeRatesWithEnvironment(): Flow<List<CachedExchangeRate>> =
    allExchangeRates.combine(rateEnvironments) { rates, envs ->
      rates.map { CachedExchangeRate(it, envs[it.key()]) }
    }

  private suspend fun storeExchangeRate(exchangeRate: ExchangeRate): Result<Unit, Error> {
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
  ): Result<Unit, Error> {
    // if the exchange rate for the same currency pair and time already exists, update it
    // otherwise, add it to the list
    historicalExchangeRates.update { currentRates ->
      val existingRates = currentRates[atTime].orEmpty()
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

  override suspend fun clear(): Result<Unit, Error> {
    allExchangeRates.value = emptyList()
    rateEnvironments.value = emptyMap()
    return Ok(Unit)
  }

  fun reset() {
    allExchangeRates.value = emptyList()
    historicalExchangeRates.value = emptyMap()
    rateEnvironments.value = emptyMap()
  }
}
