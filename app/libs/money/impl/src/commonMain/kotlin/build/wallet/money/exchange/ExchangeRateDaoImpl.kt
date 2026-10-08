package build.wallet.money.exchange

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.ExchangeRateEntity
import build.wallet.database.sqldelight.HistoricalExchangeRateEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
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

  override suspend fun storeExchangeRate(
    exchangeRate: ExchangeRate,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        val existingRates = exchangeRateQueries.allExchangeRates().executeAsList()
        val existingRate =
          existingRates.find { existing ->
            existing.fromCurrency == exchangeRate.fromCurrency &&
              existing.toCurrency == exchangeRate.toCurrency
          }
        if (existingRate == null) {
          exchangeRateQueries.insertExchangeRate(
            fromCurrency = exchangeRate.fromCurrency,
            toCurrency = exchangeRate.toCurrency,
            rate = exchangeRate.rate,
            timeRetrieved = exchangeRate.timeRetrieved,
            f8eEnvironment = f8eEnvironment
          )
        } else if (
          // A rate from a different environment always replaces the cached one: it is
          // not a staler version of the same price, it is a different price. Comparing
          // timestamps across environments would let an older-but-correct rate lose to
          // a newer one from an environment we are no longer talking to.
          existingRate.f8eEnvironment != f8eEnvironment ||
          exchangeRate.timeRetrieved > existingRate.timeRetrieved
        ) {
          exchangeRateQueries.updateExchangeRate(
            rate = exchangeRate.rate,
            fromCurrency = exchangeRate.fromCurrency,
            toCurrency = exchangeRate.toCurrency,
            timeRetrieved = exchangeRate.timeRetrieved,
            f8eEnvironment = f8eEnvironment
          )
        }
      }
      .logFailure { "Failed to exchange rate" }
  }

  override suspend fun deleteRatesNotFrom(f8eEnvironment: F8eEnvironment): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        exchangeRateQueries.deleteRatesNotFromEnvironment(f8eEnvironment)
      }
      .logFailure { "Failed to delete exchange rates from other environments" }
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

  private val allExchangeRatesWithEnvironment =
    flow {
      databaseProvider.database().exchangeRateQueries
        .allExchangeRates()
        .asFlowOfList()
        .transformLatest { queryResult ->
          queryResult
            .onSuccess { entities ->
              emit(entities.map { it.cachedExchangeRate() })
            }
            // Don't emit, nor crash on failure. The rate won't be up to date however.
            .logFailure {
              "Error reading exchange rate from database"
            }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }

  override fun allExchangeRatesWithEnvironment(): Flow<List<CachedExchangeRate>> =
    allExchangeRatesWithEnvironment

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction { exchangeRateQueries.clear() }
      .logFailure { "Failed to clear exchange rates" }
  }
}

/**
 * Convert database entity to our own type.
 */
private fun ExchangeRateEntity.cachedExchangeRate(): CachedExchangeRate {
  return CachedExchangeRate(
    exchangeRate = exchangeRate(),
    f8eEnvironment = f8eEnvironment
  )
}

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
