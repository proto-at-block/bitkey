package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

/**
 * State machine for displaying details of a single Bitcoin transaction.
 */
interface TransactionItemUiStateMachine : StateMachine<TransactionItemUiProps, ListItemModel>

data class TransactionItemUiProps(
  val transaction: BitcoinTransaction,
  val fiatCurrency: FiatCurrency,
  val onClick: (transaction: BitcoinTransaction) -> Unit,
)
