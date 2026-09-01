package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.activity.Transaction.PartnershipTransaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.activity.TransactionsActivityState
import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.bitcoin.metadata.TransactionNoteService
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.TransactionNotesFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.Some
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import com.github.michaelbull.result.getOr
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class TransactionsActivityUiStateMachineImpl(
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinTransactionItemUiStateMachine: BitcoinTransactionItemUiStateMachine,
  private val transactionsActivityService: TransactionsActivityService,
  private val partnerTransactionItemUiStateMachine: PartnerTransactionItemUiStateMachine,
  private val transactionNoteService: TransactionNoteService,
  private val transactionNotesFeatureFlag: TransactionNotesFeatureFlag,
) : TransactionsActivityUiStateMachine {
  @Composable
  override fun model(props: TransactionsActivityProps): TransactionsActivityModel? {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val transactionsState by remember { transactionsActivityService.transactionsState }
      .collectAsState()
    val areTransactionNotesEnabled by remember {
      transactionNotesFeatureFlag.flagValue().map { it.isEnabled() }
    }.collectAsState(initial = transactionNotesFeatureFlag.isEnabled())

    // Observe notes from the database so that any note edit is immediately
    // reflected in the list, no matter where the edit happened.
    val transactionNotesById by remember(areTransactionNotesEnabled) {
      when {
        areTransactionNotesEnabled ->
          transactionNoteService.notes().map { result ->
            result
              .logFailure { "Failed to load transaction notes for transactions list." }
              .getOr(emptyMap())
          }
        else -> flowOf(emptyMap<BitcoinTransactionId, TransactionNote>())
      }
    }.collectAsState(initial = emptyMap())

    // Trigger initial sync on first composition
    LaunchedEffect("initial-transactions-load") {
      transactionsActivityService.sync()
    }

    // Return null for empty state
    if (transactionsState is TransactionsActivityState.Empty) return null

    val isLoading = transactionsState is TransactionsActivityState.InitialLoading
    val transactions = when (val state = transactionsState) {
      is TransactionsActivityState.Loaded -> state.transactions
      else -> null
    }

    val numberOfSkeletonItems = props.transactionVisibility.numberOfSkeletonTransactions

    val transactionsToShow = remember(transactions, props.transactionVisibility) {
      when (val visibility = props.transactionVisibility) {
        is All -> transactions
        is Some -> transactions?.take(visibility.numberOfVisibleTransactions)?.toImmutableList()
      }
    }

    val listModel = ListGroupModel(
      style = ListGroupStyle.NONE,
      items = if (isLoading) {
        List(numberOfSkeletonItems) { SkeletonTransactionItemModel() }.toImmutableList()
      } else if (transactionsToShow == null) {
        // After load attempt, if still null, show empty list
        emptyList<Nothing>().toImmutableList()
      } else {
        transactionsToShow.map {
          when (it) {
            is BitcoinWalletTransaction -> bitcoinTransactionItemUiStateMachine.model(
              props = BitcoinTransactionItemUiProps(
                transaction = it,
                fiatCurrency = fiatCurrency,
                transactionNote = transactionNotesById[BitcoinTransactionId(it.details.id)]?.note,
                onClick = props.onTransactionClicked
              )
            )
            is PartnershipTransaction -> partnerTransactionItemUiStateMachine.model(
              props = PartnerTransactionItemUiProps(
                transaction = it,
                // Partnership transactions with an on-chain transaction can have a note
                // keyed by that txid (added from the transaction details screen).
                transactionNote = it.bitcoinTransaction?.id
                  ?.let { txid -> transactionNotesById[BitcoinTransactionId(txid)]?.note },
                onClick = props.onTransactionClicked
              )
            )
          }
        }.toImmutableList()
      }
    )

    return TransactionsActivityModel(
      listModel = listModel,
      hasMoreTransactions = !isLoading && ((transactions?.size ?: 0) > (transactionsToShow?.size ?: 0))
    )
  }
}
