package build.wallet.statemachine.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility.Some
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class TransactionListUiStateMachineImpl(
  private val transactionItemUiStateMachine: TransactionItemUiStateMachine,
) : TransactionListUiStateMachine {
  @Composable
  override fun model(props: TransactionListUiProps): ImmutableList<ListGroupModel>? {
    // Collect pending and confirmed transactions based on the latest transactions
    val (pendingTransactions, confirmedTransactions) = rememberTransactions(props)

    // Return the model, or null if the list of transactions is empty
    return when {
      props.transactions.isEmpty() -> null

      else ->
        buildImmutableList {
          if (pendingTransactions.isNotEmpty() && confirmedTransactions.isNotEmpty()) {
            add(TransactionSectionModel(props, pendingTransactions))
            add(TransactionSectionModel(props, confirmedTransactions))
          } else {
            if (pendingTransactions.isNotEmpty()) {
              add(TransactionSectionModel(props, pendingTransactions))
            } else if (confirmedTransactions.isNotEmpty()) {
              add(TransactionSectionModel(props, confirmedTransactions))
            }
          }
        }
    }
  }

  /**
   * First list in pair is pending transactions, second list is confirmed transactions.
   */
  @Composable
  private fun rememberTransactions(
    props: TransactionListUiProps,
  ): Pair<ImmutableList<BitcoinTransaction>, ImmutableList<BitcoinTransaction>> {
    return remember(
      props.transactions,
      props.transactionVisibility
    ) {
      when (val visibility = props.transactionVisibility) {
        is All -> {
          val (pending, confirmed) = props.transactions.partition { it.confirmationStatus is Pending }
          Pair(pending.toImmutableList(), confirmed.toImmutableList())
        }

        is Some -> {
          var (pending, confirmed) = props.transactions.partition { it.confirmationStatus is Pending }
          // First, try to take the pending transactions
          pending = pending.take(visibility.numberOfVisibleTransactions)
          // Then, take whatever remaining number from confirmed
          val remaining = visibility.numberOfVisibleTransactions - pending.size
          confirmed = confirmed.take(remaining)
          Pair(pending.toImmutableList(), confirmed.toImmutableList())
        }
      }
    }
  }

  // Helper function to create a section model from a list of transactions
  @Composable
  private fun TransactionSectionModel(
    props: TransactionListUiProps,
    transactions: ImmutableList<BitcoinTransaction>,
  ) = ListGroupModel(
    header = null,
    style = ListGroupStyle.NONE,
    items =
      transactions.map {
        transactionItemUiStateMachine.model(
          props =
            TransactionItemUiProps(
              transaction = it,
              fiatCurrency = props.fiatCurrency,
              onClick = props.onTransactionClicked
            )
        )
      }.toImmutableList()
  )
}
