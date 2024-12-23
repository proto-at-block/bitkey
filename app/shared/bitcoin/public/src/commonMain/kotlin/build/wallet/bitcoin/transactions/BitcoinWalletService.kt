package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.wallet.SpendingWallet
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Domain service for working with the bitcoin wallet associated with the active account.
 */
interface BitcoinWalletService {
  /**
   * On demand request to sync wallet. Updated wallet balance and transactions will be emitted to
   * [transactionsData].
   */
  suspend fun sync(): Result<Unit, Error>

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
  fun transactionsData(): StateFlow<TransactionsData?>

  suspend fun broadcast(
    psbt: Psbt,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<BroadcastDetail, Error>

  /**
   * Create a PSBTs for the given send amount in all the [EstimatedTransactionPriority]s available.
   *
   * @param sendAmount - the amount to send
   * @param recipientAddress - the address to send to
   */
  suspend fun createPsbtsForSendAmount(
    sendAmount: BitcoinTransactionSendAmount,
    recipientAddress: BitcoinAddress,
  ): Result<Map<EstimatedTransactionPriority, Psbt>, Error>
}

suspend fun BitcoinWalletService.getTransactionData(): TransactionsData =
  transactionsData().filterNotNull().first()
