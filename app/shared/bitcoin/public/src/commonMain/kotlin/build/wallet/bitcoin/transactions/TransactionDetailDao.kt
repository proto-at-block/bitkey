package build.wallet.bitcoin.transactions

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

interface TransactionDetailDao {
  /**
   * Inserts the given broadcast time for the transaction with the
   * given ID.
   */
  suspend fun insert(
    broadcastTime: Instant,
    transactionId: String,
    estimatedConfirmationTime: Instant,
  ): Result<Unit, DbError>

  /**
   * Returns the time at which the transaction with the given ID was
   * broadcast. If no detail exists or an error occurs, returns null.
   */
  suspend fun broadcastTimeForTransaction(transactionId: String): Instant?

  /**
   * Returns the confirmation time we promised the customer based on the fee rate they selected.
   */
  suspend fun confirmationTimeForTransaction(transactionId: String): Instant?

  /**
   * Clears all details for all transactions from the dao
   */
  suspend fun clear(): Result<Unit, DbError>
}
