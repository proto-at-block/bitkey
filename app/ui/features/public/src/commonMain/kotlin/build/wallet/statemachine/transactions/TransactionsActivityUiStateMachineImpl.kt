package build.wallet.statemachine.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.activity.Transaction.PartnershipTransaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.Some
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class TransactionsActivityUiStateMachineImpl(
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinTransactionItemUiStateMachine: BitcoinTransactionItemUiStateMachine,
  private val transactionsActivityService: TransactionsActivityService,
  private val partnerTransactionItemUiStateMachine: PartnerTransactionItemUiStateMachine,
) : TransactionsActivityUiStateMachine {
  @Composable
  override fun model(props: TransactionsActivityProps): TransactionsActivityModel? {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val transactions by remember { transactionsActivityService.transactions }
      .collectAsState()

    if (transactions.isNullOrEmpty()) return null

    val transactionsToShow = remember(transactions, props.transactionVisibility) {
      when (val visibility = props.transactionVisibility) {
        is All -> transactions
        is Some -> transactions?.take(visibility.numberOfVisibleTransactions)?.toImmutableList()
      } ?: error("Transactions should not be since we short circuit above")
    }

    val listModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = transactionsToShow.map {
        when (it) {
          is BitcoinWalletTransaction -> bitcoinTransactionItemUiStateMachine.model(
            props = BitcoinTransactionItemUiProps(
              transaction = it,
              fiatCurrency = fiatCurrency,
              onClick = props.onTransactionClicked
            )
          )
          is PartnershipTransaction -> partnerTransactionItemUiStateMachine.model(
            props = PartnerTransactionItemUiProps(
              transaction = it,
              onClick = props.onTransactionClicked
            )
          )
        }
      }.toImmutableList()
    )

    return TransactionsActivityModel(
      listModel = listModel,
      hasMoreTransactions = (transactions?.size ?: 0) > transactionsToShow.size
    )
  }
}
