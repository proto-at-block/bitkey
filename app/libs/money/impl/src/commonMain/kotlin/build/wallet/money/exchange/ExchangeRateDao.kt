package build.wallet.money.exchange

import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * A locally cached [ExchangeRate] together with the f8e environment that produced it.
 * [f8eEnvironment] is null for rows written before provenance was recorded.
 */
data class CachedExchangeRate(
  val exchangeRate: ExchangeRate,
  val f8eEnvironment: F8eEnvironment?,
)

interface ExchangeRateDao {
  /**
   * Updates local exchange rate, recording the [f8eEnvironment] it was fetched from.
   *
   * Provenance is stored alongside the rate so it survives process restarts: a rate is
   * only valid for the environment that produced it, and different environments serve
   * different prices.
   */
  suspend fun storeExchangeRate(
    exchangeRate: ExchangeRate,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error>

  /**
   * Deletes any cached rates that did not come from [f8eEnvironment], including rows
   * written before provenance was recorded (whose origin is unknown).
   */
  suspend fun deleteRatesNotFrom(f8eEnvironment: F8eEnvironment): Result<Unit, Error>

  /**
   * Emits list of latest exchange rates (currently we only sync BTC:USD rate).
   */
  fun allExchangeRates(): Flow<List<ExchangeRate>>

  /**
   * Emits latest exchange rates paired with the f8e environment they were fetched from.
   *
   * Callers that convert money must use this and filter to the active environment:
   * rates from another environment reflect a different price, not a staler one. Rows
   * written before provenance was recorded have a `null` environment and are never
   * attributable to the active one.
   */
  fun allExchangeRatesWithEnvironment(): Flow<List<CachedExchangeRate>>

  /**
   * Updates the local historical exchange rate for the given time.
   */
  suspend fun storeHistoricalExchangeRate(
    exchangeRate: ExchangeRate,
    atTime: Instant,
  ): Result<Unit, Error>

  /**
   * Returns all of the local historical exchange rates for the given time.
   */
  suspend fun historicalExchangeRatesAtTime(time: Instant): List<ExchangeRate>?

  /**
   * Clears all exchange rates from local storage.
   * Used for debug/testing purposes only.
   */
  suspend fun clear(): Result<Unit, Error>
}
