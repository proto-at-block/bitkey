package build.wallet.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the collection of locally known partnership transactions.
 */
interface PartnershipTransactionsStatusRepository {
  /**
   * Observes the current list of partnership transactions.
   */
  val transactions: Flow<List<PartnershipTransaction>>

  /**
   * Synchronizes the local list of partnership transactions with the remote server.
   */
  suspend fun sync()

  /**
   * Deletes all locally known partnership transactions.
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Create a new transaction from a partner for tracking locally.
   */
  suspend fun create(
    partnerInfo: PartnerInfo,
    type: PartnershipTransactionType,
  ): Result<PartnershipTransaction, Error>

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
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error>
}
