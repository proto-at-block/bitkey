package build.wallet.activity

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnershipTransactionsService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.*

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

  override val transactions: Flow<List<Transaction>> = transactionsCache.filterNotNull()
}
