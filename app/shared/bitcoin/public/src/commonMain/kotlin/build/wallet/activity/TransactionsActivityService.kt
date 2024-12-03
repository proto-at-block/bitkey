package build.wallet.activity

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

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
  val transactions: Flow<List<Transaction>>
}
