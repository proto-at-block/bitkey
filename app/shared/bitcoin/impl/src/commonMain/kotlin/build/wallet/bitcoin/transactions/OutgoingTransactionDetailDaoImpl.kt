package build.wallet.bitcoin.transactions

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.exchange.ExchangeRate
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.datetime.Instant

class OutgoingTransactionDetailDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : OutgoingTransactionDetailDao {
  override suspend fun insert(
    broadcastTime: Instant,
    transactionId: String,
    estimatedConfirmationTime: Instant,
    exchangeRates: List<ExchangeRate>?,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .awaitTransaction {
        transactionDetailQueries.insertTransactionDetail(
          transactionId = transactionId,
          broadcastTime = broadcastTime,
          estimatedConfirmationTime = estimatedConfirmationTime
        )
        exchangeRates?.forEach {
          historicalExchangeRateQueries.insertHistoricalExchangeRate(
            fromCurrency = it.fromCurrency,
            toCurrency = it.toCurrency,
            rate = it.rate,
            time = broadcastTime
          )
        }
      }
      .logFailure { "Failed to insert details for transaction $transactionId" }

  override suspend fun broadcastTimeForTransaction(transactionId: String): Instant? =
    databaseProvider.database()
      .transactionDetailQueries
      .awaitTransactionWithResult {
        broadcastTimeForTransaction(transactionId = transactionId).executeAsOneOrNull()
      }
      .logFailure { "Failed to fetch broadcast time for transaction $transactionId" }
      .get()

  override suspend fun confirmationTimeForTransaction(transactionId: String): Instant? =
    databaseProvider.database()
      .transactionDetailQueries
      .awaitTransactionWithResult {
        estimatedConfirmationTimeForTransaction(
          transactionId = transactionId
        ).executeAsOneOrNull()
      }
      .logFailure { "Failed to fetch estimated confirmation time for transaction $transactionId" }
      .get()

  override suspend fun clear(): Result<Unit, DbError> =
    databaseProvider.database()
      .transactionDetailQueries
      .awaitTransaction {
        clear()
      }
      .logFailure { "Failed to clear transaction details" }
}
