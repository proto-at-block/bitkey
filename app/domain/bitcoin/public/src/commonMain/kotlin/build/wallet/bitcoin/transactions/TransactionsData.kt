package build.wallet.bitcoin.transactions

import androidx.compose.runtime.Immutable
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.money.FiatMoney
import kotlinx.collections.immutable.ImmutableList

/**
 * Represents the balance and transaction data for the active wallet.
 *
 * Tracks the wallet bitcoin balance and on-chain transaction history associated with the active wallet.
 * Currently, only the active keyset is utilized to manage balance and transactions, inactive
 * keysets are not currently taken into consideration.
 *
 * @property balance The Bitcoin balance of the active wallet.
 * @property transactions The on-chain Bitcoin transaction history of the active wallet.
 */
@Immutable
data class TransactionsData(
  val balance: BitcoinBalance,
  val fiatBalance: FiatMoney?,
  val transactions: ImmutableList<BitcoinTransaction>,
  val utxos: Utxos,
)
