package build.wallet.activity

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service that provides transactions activity for active account.
 *
 * The transactions are periodically synced by [TransactionsActivitySyncWorker] from
 * the active bitcoin on-chain wallet and partnerships service. Latest transactions are
 * emitted by [transactions].
 */
interface TransactionsActivityService {
  /**
   * On-demand request to sync on-chain transactions and partnerships transactions.
   */
  suspend fun sync(): Result<Unit, Error>

  /**
   * Emits latest list of transactions activity.
   */
  val transactions: StateFlow<List<Transaction>?>

  /**
   * A flow of the latest transaction fetched by its [transactionId], null when none is found.
   */
  fun transactionById(transactionId: String): Flow<Transaction?>
}
