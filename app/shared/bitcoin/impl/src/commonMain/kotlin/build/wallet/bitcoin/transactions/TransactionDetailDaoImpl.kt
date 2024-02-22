package build.wallet.bitcoin.transactions

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.datetime.Instant

class TransactionDetailDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : TransactionDetailDao {
  override suspend fun insert(
    broadcastTime: Instant,
    transactionId: String,
    estimatedConfirmationTimeInstant: Instant,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .transactionDetailQueries
      .awaitTransaction {
        insertTransactionDetail(
          transactionId = transactionId,
          broadcastTimeInstant = broadcastTime,
          estimatedConfirmationTimeInstant = estimatedConfirmationTimeInstant
        )
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
        ).executeAsOneOrNull()?.estimatedConfirmationTimeInstant
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
