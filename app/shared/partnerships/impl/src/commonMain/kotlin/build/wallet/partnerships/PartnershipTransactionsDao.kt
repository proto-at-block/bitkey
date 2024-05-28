package build.wallet.partnerships

import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to locally stored partnership transactions.
 */
interface PartnershipTransactionsDao {
  /**
   * Creates or updates a partnership transaction.
   */
  suspend fun save(transaction: PartnershipTransaction): Result<Unit, DbTransactionError>

  /**
   * Get a list of all locally stored partnership transactions.
   */
  fun getTransactions(): Flow<Result<List<PartnershipTransaction>, DbTransactionError>>

  /**
   * Get a transaction by a specified ID
   */
  suspend fun getById(
    id: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, DbTransactionError>

  /**
   * Delete a single partnership transaction's local information.
   */
  suspend fun deleteTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<Unit, DbTransactionError>

  /**
   * Clear all locally stored partnership transactions.
   */
  suspend fun clear(): Result<Unit, DbTransactionError>
}
