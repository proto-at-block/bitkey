package build.wallet.partnerships

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Provides access to the collection of locally known partnership transactions.
 */
interface PartnershipTransactionsService {
  /**
   * Observes the current list of partnership transactions.
   */
  val transactions: Flow<List<PartnershipTransaction>>

  /**
   * Observes the partner IDs that have been used in transactions.
   */
  val previouslyUsedPartnerIds: Flow<List<PartnerId>>

  /**
   * Syncs the status of currently pending transactions (if any).
   */
  suspend fun syncPendingTransactions(): Result<Unit, Error>

  /**
   * Deletes all locally known partnership transactions.
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Create a new transaction from a partner for tracking locally.
   */
  suspend fun create(
    id: PartnershipTransactionId,
    partnerInfo: PartnerInfo,
    type: PartnershipTransactionType,
  ): Result<PartnershipTransaction, Error>

  /**
   * Get and update the status of the most recent transaction for a
   * specified partner.
   *
   * @param partnerId The ID of the partner to lookup transactions for.
   * @param status The new status to set the transaction to.
   * @param recency The maximum age of the transaction to find.
   * @return The updated transaction, or `null` if no recent transaction was found.
   */
  suspend fun updateRecentTransactionStatusIfExists(
    partnerId: PartnerId,
    status: PartnershipTransactionStatus,
    recency: Duration = 30.minutes,
  ): Result<PartnershipTransaction?, Error>

  /**
   * Immediately update the specified transaction.
   *
   * This will update the local copy of the transaction before returning it,
   * including deleting the transaction if it is not found in the API.
   * If this fails to fetch an update, it will return an error rather than
   * falling back to the local transaction. The local transaction will not
   * be modified.
   */
  suspend fun syncTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error>
}
