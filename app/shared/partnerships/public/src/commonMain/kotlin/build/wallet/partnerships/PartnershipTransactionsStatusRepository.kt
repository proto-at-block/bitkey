package build.wallet.partnerships

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
}
