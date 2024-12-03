package build.wallet.money.exchange

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.ExchangeRateEntity
import build.wallet.database.sqldelight.HistoricalExchangeRateEntity
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

class ExchangeRateDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : ExchangeRateDao {
  override suspend fun storeHistoricalExchangeRate(
    exchangeRate: ExchangeRate,
    atTime: Instant,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        historicalExchangeRateQueries.insertHistoricalExchangeRate(
          fromCurrency = exchangeRate.fromCurrency,
          toCurrency = exchangeRate.toCurrency,
          rate = exchangeRate.rate,
          time = atTime
        )
      }
      .logFailure { "Unable to store historical exchange rate" }
  }

  override suspend fun historicalExchangeRatesAtTime(time: Instant): List<ExchangeRate>? {
    return databaseProvider.database().historicalExchangeRateQueries
      .allHistoricalExchangeRatesAtTime(time)
      .awaitAsListResult()
      .logFailure { "Unable to fetch historical exchange rate" }
      .get()?.let { list ->
        list.map { it.exchangeRate() }
      }
  }

  override suspend fun storeExchangeRate(exchangeRate: ExchangeRate): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        val existingRates = exchangeRateQueries.allExchangeRates().executeAsList()
        val existingRate =
          existingRates.find {
            it.fromCurrency == exchangeRate.fromCurrency &&
              it.toCurrency == exchangeRate.toCurrency
          }
        if (existingRate == null) {
          exchangeRateQueries.insertExchangeRate(
            fromCurrency = exchangeRate.fromCurrency,
            toCurrency = exchangeRate.toCurrency,
            rate = exchangeRate.rate,
            timeRetrieved = exchangeRate.timeRetrieved
          )
        } else if (exchangeRate.timeRetrieved > existingRate.timeRetrieved) {
          exchangeRateQueries.updateExchangeRate(
            rate = exchangeRate.rate,
            fromCurrency = exchangeRate.fromCurrency,
            toCurrency = exchangeRate.toCurrency,
            timeRetrieved = exchangeRate.timeRetrieved
          )
        }
      }
      .logFailure { "Failed to exchange rate" }
  }

  private val allExchangeRates =
    flow {
      databaseProvider.database().exchangeRateQueries
        .allExchangeRates()
        .asFlowOfList()
        .transformLatest { queryResult ->
          queryResult
            .onSuccess { entities ->
              emit(entities.map { it.exchangeRate() })
            }
            // Don't emit, nor crash on failure. The rate won't be up to date however.
            .logFailure {
              "Error reading exchange rate from database"
            }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }

  override fun allExchangeRates(): Flow<List<ExchangeRate>> = allExchangeRates
}

/**
 * Convert database entity to our own type.
 */
private fun ExchangeRateEntity.exchangeRate(): ExchangeRate {
  return ExchangeRate(
    fromCurrency = fromCurrency,
    toCurrency = toCurrency,
    rate = rate,
    timeRetrieved = timeRetrieved
  )
}

private fun HistoricalExchangeRateEntity.exchangeRate(): ExchangeRate {
  return ExchangeRate(
    fromCurrency = fromCurrency,
    toCurrency = toCurrency,
    rate = rate,
    timeRetrieved = time
  )
}
