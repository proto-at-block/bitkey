package build.wallet.bitcoin.transactions

import androidx.compose.runtime.Immutable
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.money.FiatMoney
import kotlinx.collections.immutable.ImmutableList

/**
 * Describes balance and transaction data for active keybox. Currently only uses active spending
 * keyset of the keybox to keep track of balance and transactions - inactive keysets are not used
 * yet.
 *
 * Balance and transactions are periodically synced.
 *
 * @property balance bitcoin balance of active keybox.
 * @property transactions on-chain bitcoin transaction history of active keybox.
 * @property syncTransactions manually requests blockchain sync for current keybox, which in turn
 * updates balance and transaction history.
 */
sealed interface TransactionsData {
  data object LoadingTransactionsData : TransactionsData

  @Immutable
  data class TransactionsLoadedData(
    val balance: BitcoinBalance,
    val fiatBalance: FiatMoney?,
    val transactions: ImmutableList<BitcoinTransaction>,
    val unspentOutputs: ImmutableList<BdkUtxo>,
  ) : TransactionsData
}
