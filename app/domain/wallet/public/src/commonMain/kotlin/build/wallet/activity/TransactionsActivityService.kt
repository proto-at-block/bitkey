package build.wallet.activity

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents the state of transactions activity.
 */
sealed interface TransactionsActivityState {
  /**
   * Initial loading state before the first sync attempt.
   */
  data object InitialLoading : TransactionsActivityState

  /**
   * Transactions have been loaded.
   */
  data class Loaded(val transactions: List<Transaction>) : TransactionsActivityState

  /**
   * No transactions available after loading.
   */
  data object Empty : TransactionsActivityState
}

/**
 * Domain service that provides transactions activity for active account.
 *
 * The transactions are periodically synced by [TransactionsActivitySyncWorker] from
 * the active bitcoin on-chain wallet and partnerships service. Latest transactions are
 * emitted by [transactionsState].
 */
interface TransactionsActivityService {
  /**
   * On-demand request to sync on-chain transactions and partnerships transactions.
   */
  suspend fun sync(): Result<Unit, Error>

  /**
   * Emits transaction activity state including initial loading state.
   * This is the preferred property to observe for UI consumption.
   */
  val transactionsState: StateFlow<TransactionsActivityState>

  /**
   * Emits latest list of transactions activity.
   * @deprecated Use [transactionsState] instead for proper loading state handling.
   */
  @Deprecated("Use transactionsState instead")
  val transactions: StateFlow<List<Transaction>?>

  /**
   * Emits latest list of transactions activity including inactive wallet transactions.
   */
  val activeAndInactiveWalletTransactions: StateFlow<List<Transaction>?>

  /**
   * A flow of the latest transaction fetched by its [transactionId], null when none is found.
   */
  fun transactionById(transactionId: String): Flow<Transaction?>
}
