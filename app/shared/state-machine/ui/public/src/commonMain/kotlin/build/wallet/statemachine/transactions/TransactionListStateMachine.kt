package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for showing list of transactions.
 */
interface TransactionListUiStateMachine :
  StateMachine<TransactionListUiProps, ImmutableList<ListGroupModel>?>

data class TransactionListUiProps(
  val transactionVisibility: TransactionVisibility,
  val transactions: ImmutableList<BitcoinTransaction>,
  val fiatCurrency: FiatCurrency,
  val onTransactionClicked: (transaction: BitcoinTransaction) -> Unit,
) {
  sealed interface TransactionVisibility {
    data object All : TransactionVisibility

    data class Some(val numberOfVisibleTransactions: Int) : TransactionVisibility
  }
}
