package build.wallet.activity

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnershipTransactionsService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class TransactionsActivityServiceImpl(
  private val expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val bitcoinWalletService: BitcoinWalletService,
) : TransactionsActivityService, TransactionsActivitySyncWorker {
  override suspend fun sync(): Result<Unit, Error> =
    coroutineBinding {
      bitcoinWalletService.sync().bind()
      partnershipTransactionsService.syncPendingTransactions().bind()
    }.logFailure { "Error syncing transactions activity. " }

  override suspend fun executeWork() {
    val expectedTransactionsEnabledFlow = expectedTransactionsPhase2FeatureFlag.flagValue()
    val bitcoinTransactionsFlow =
      bitcoinWalletService.transactionsData().map { it?.transactions.orEmpty() }
    val partnershipsTransactionsFlow = partnershipTransactionsService.transactions
      // filter out the null status partnership transactions since these have not been synced with f8e
      .map { transactions -> transactions.filter { it.status != null } }

    // Currently emits all transactions together (partnership + on-chain) without matching.
    combine(
      expectedTransactionsEnabledFlow, bitcoinTransactionsFlow, partnershipsTransactionsFlow
    ) { expectedTransactionsEnabled, bitcoinTransactions, expectedTransactions ->
      if (expectedTransactionsEnabled.value) {
        mergeAndSortTransactions(
          partnershipTransactions = expectedTransactions,
          bitcoinTransactions = bitcoinTransactions
        )
      } else {
        bitcoinTransactions.map { BitcoinWalletTransaction(details = it) }
      }
    }.collect(transactionsCache)
  }

  private val transactionsCache = MutableStateFlow<List<Transaction>?>(null)

  override val transactions: StateFlow<List<Transaction>?> = transactionsCache

  override fun transactionById(transactionId: String): Flow<Transaction?> {
    return transactions.map { it?.find { tx -> tx.id == transactionId } }
      .distinctUntilChanged()
  }
}
