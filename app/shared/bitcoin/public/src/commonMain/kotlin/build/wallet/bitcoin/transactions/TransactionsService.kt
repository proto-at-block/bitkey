package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.wallet.SpendingWallet
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service working with transaction data associated with a given wallet.
 */
interface TransactionsService {
  /**
   * Manually sync the latest transactions data. Updated data will be present in [transactionsData].
   */
  suspend fun syncTransactions(): Result<Unit, Error>

  /**
   * The [SpendingWallet] for the current account. Only a Full Account has a spending wallet today.
   */
  fun spendingWallet(): StateFlow<SpendingWallet?>

  /**
   * Returns the latest [TransactionsData] associated with the logged in account. This will update
   * whenever:
   * - the balance changes
   * - the transactions change
   * - utxos change
   * - currency preference changes
   * - exchange rates change
   */
  fun transactionsData(): StateFlow<TransactionsData>
}
