package build.wallet.money.exchange

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface ExchangeRateDao {
  /**
   * Updates local exchange rate.
   */
  suspend fun storeExchangeRate(exchangeRate: ExchangeRate): Result<Unit, DbError>

  /**
   * Emits list of latest exchange rates (currently we only sync BTC:USD rate).
   */
  fun allExchangeRates(): Flow<List<ExchangeRate>>

  /**
   * Updates the local historical exchange rate for the given time.
   */
  suspend fun storeHistoricalExchangeRate(
    exchangeRate: ExchangeRate,
    atTime: Instant,
  ): Result<Unit, DbError>

  /**
   * Returns all of the local historical exchange rates for the given time.
   */
  suspend fun historicalExchangeRatesAtTime(time: Instant): List<ExchangeRate>?
}
