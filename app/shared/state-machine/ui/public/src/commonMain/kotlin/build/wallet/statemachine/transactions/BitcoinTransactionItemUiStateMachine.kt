package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

/**
 * State machine for displaying details of a single Bitcoin transaction.
 */
interface BitcoinTransactionItemUiStateMachine : StateMachine<BitcoinTransactionItemUiProps, ListItemModel>

data class BitcoinTransactionItemUiProps(
  val transaction: BitcoinWalletTransaction,
  val fiatCurrency: FiatCurrency,
  val onClick: (transaction: BitcoinWalletTransaction) -> Unit,
)
