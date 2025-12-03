package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.Some
import build.wallet.ui.model.Model
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for showing wallet's transaction activity in a list.
 *
 * Returns [TransactionsActivityModel] containing list item models for wallet's transactions.
 * If wallet has no transactions, `null` is returned.
 */
interface TransactionsActivityUiStateMachine :
  StateMachine<TransactionsActivityProps, TransactionsActivityModel?>

/**
 * @param transactionVisibility determines if all or only recent transactions
 * (up to [Some.numberOfVisibleTransactions]) should be shown.
 */
data class TransactionsActivityProps(
  val transactionVisibility: TransactionVisibility,
  val onTransactionClicked: (transaction: Transaction) -> Unit,
) {
  sealed interface TransactionVisibility {
    val numberOfSkeletonTransactions: Int

    /**
     * All wallet's transactions will be shown.
     */
    data object All : TransactionVisibility {
      // Only show 5 skeleton transactions when loading
      override val numberOfSkeletonTransactions: Int = DEFAULT_VISIBLE_TRANSACTIONS
    }

    /**
     * Only recent transactions up to [numberOfVisibleTransactions] amount will be shown.
     */
    data class Some(
      val numberOfVisibleTransactions: Int = DEFAULT_VISIBLE_TRANSACTIONS,
    ) : TransactionVisibility {
      override val numberOfSkeletonTransactions: Int = numberOfVisibleTransactions
    }

    companion object {
      private const val DEFAULT_VISIBLE_TRANSACTIONS = 5
    }
  }
}

/**
 * Model returned by [TransactionsActivityUiStateMachine].
 * Represents the list of transactions activity emitted by [TransactionsActivityService].
 *
 * @param hasMoreTransactions - returns `false` if [listModel] is fully representative of all
 * available transactions. Returns `true` if [listModel] does not contain all transactions. This is
 * possible when [TransactionsActivityProps.TransactionVisibility.Some] is used where number of
 * visible transactions is less than total transactions.
 */
data class TransactionsActivityModel(
  val listModel: ListGroupModel,
  val hasMoreTransactions: Boolean,
) : Model
