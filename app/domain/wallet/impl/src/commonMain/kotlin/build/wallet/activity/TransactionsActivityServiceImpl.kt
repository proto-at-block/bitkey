package build.wallet.activity

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.balance.utils.MockScenarioService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletProvider
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnershipTransactionsService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class TransactionsActivityServiceImpl(
  private val expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
  private val watchingWalletProvider: WatchingWalletProvider,
  private val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val appScope: CoroutineScope,
  private val mockScenarioService: MockScenarioService,
) : TransactionsActivityService, TransactionsActivitySyncWorker {
  private val transactionsCache = MutableStateFlow<List<Transaction>?>(null)

  override val transactions: StateFlow<List<Transaction>?> = transactionsCache

  private val activeAndInactiveWalletTransactionsCache = MutableStateFlow<List<Transaction>?>(null)
  override val activeAndInactiveWalletTransactions: StateFlow<List<Transaction>?> = activeAndInactiveWalletTransactionsCache
  private val inactiveWallets =
    appScope
      .async(Dispatchers.IO, start = LAZY) {
        fetchAndSyncInactiveWallets()
      }

  override suspend fun sync(): Result<Unit, Error> =
    coroutineBinding {
      bitcoinWalletService.sync().bind()
      partnershipTransactionsService.syncPendingTransactions().bind()
      syncInactiveWallets().bind()
    }.logFailure { "Error syncing transactions activity. " }

  override suspend fun syncActiveAndInactiveWallets(): Result<Unit, Error> =
    coroutineBinding {
      bitcoinWalletService.sync().bind()
      partnershipTransactionsService.syncPendingTransactions().bind()

      val transactions = mockScenarioService.currentTransactionScenario()?.let { _ ->
        mockScenarioService.generateTransactions()
      } ?: loadActiveAndInactiveWalletTransactions()

      activeAndInactiveWalletTransactionsCache.value = transactions
    }.logFailure { "Error syncing transactions activity with inactive wallets. " }

  override suspend fun executeWork() {
    val expectedTransactionsEnabledFlow = expectedTransactionsPhase2FeatureFlag.flagValue()
    val bitcoinTransactionsFlow = bitcoinWalletService.transactionsData().map {
      it?.transactions.orEmpty()
    }
    val partnershipsTransactionsFlow = partnershipTransactionsService.transactions
      .map { transactions -> transactions.filter { it.status != null } }

    combine(
      expectedTransactionsEnabledFlow,
      bitcoinTransactionsFlow,
      partnershipsTransactionsFlow,
      mockScenarioService.currentTransactionScenarioFlow()
    ) { expectedTransactionsEnabled, bitcoinTransactions, expectedTransactions, transactionScenario ->
      transactionScenario?.let { _ ->
        return@combine mockScenarioService.generateTransactions()
      }

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

  override fun transactionById(transactionId: String): Flow<Transaction?> {
    return transactions.map { it?.find { tx -> tx.id == transactionId } }
      .distinctUntilChanged()
  }

  /**
   * Sync inactive wallets to ensure we have complete transaction history.
   */
  private suspend fun syncInactiveWallets(): Result<Unit, Error> {
    // Trigger the sync but don't propagate errors to avoid breaking the main sync
    inactiveWallets.await().getOrElse {
      logError(throwable = it) { "Failed to sync inactive wallets" }
      emptyList()
    }
    return Ok(Unit)
  }

  /**
   * Get all Bitcoin transactions from both active and inactive wallets.
   */
  private fun getAllBitcoinTransactionsFlow(): Flow<List<BitcoinTransaction>> {
    return combine(
      bitcoinWalletService.transactionsData().map { it?.transactions.orEmpty() },
      getInactiveWalletTransactionsFlow()
    ) { activeTransactions, inactiveTransactions ->
      (activeTransactions + inactiveTransactions).sortedBy { it.confirmationTime() }
    }
  }

  /**
   * Get transactions from inactive wallets.
   */
  private fun getInactiveWalletTransactionsFlow(): Flow<List<BitcoinTransaction>> {
    return flow {
      val wallets = inactiveWallets.await().getOrElse {
        logError(throwable = it) { "Failed to load inactive wallets" }
        emptyList()
      }

      val transactions = wallets.flatMap { wallet ->
        wallet.transactions().first().filter {
          it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
        }
      }

      emit(transactions)
    }
  }

  private suspend fun loadActiveAndInactiveWalletTransactions(): List<Transaction>? {
    val expectedTransactionsEnabled = expectedTransactionsPhase2FeatureFlag.flagValue().first()
    val bitcoinTransactions = getAllBitcoinTransactionsFlow().first()
    val expectedTransactions = partnershipTransactionsService.transactions
      .map { transactions -> transactions.filter { it.status != null } }
      .first()

    return if (expectedTransactionsEnabled.value) {
      mergeAndSortTransactions(
        partnershipTransactions = expectedTransactions,
        bitcoinTransactions = bitcoinTransactions
      )
    } else {
      bitcoinTransactions.map { BitcoinWalletTransaction(details = it) }
    }
  }

  /**
   * Using the current account in [AccountService], fetch the inactive keysets
   * as [WatchingWallet] instances and sync them.
   * Returns empty list if no account is present.
   */
  private suspend fun fetchAndSyncInactiveWallets(): Result<List<WatchingWallet>, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().getOrElse {
        return@coroutineBinding emptyList<WatchingWallet>()
      }

      val environment = account.config.f8eEnvironment
      val activeKeysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

      listKeysetsF8eClient
        .listKeysets(environment, account.accountId)
        .logFailure { "Error fetching keysets for comprehensive transaction tracking." }
        .bind()
        .keysets
        .filter { it.f8eSpendingKeyset.keysetId != activeKeysetId }
        .map {
          val descriptor = it.toWalletDescriptor(account.config.bitcoinNetworkType)
          async(Dispatchers.IO) {
            watchingWalletProvider.getWallet(descriptor)
              .onSuccess { wallet ->
                wallet.sync()
              }
              .bind()
          }
        }.awaitAll()
    }.logFailure { "Failed to load inactive wallets" }

  private fun SpendingKeyset.toWalletDescriptor(
    networkType: BitcoinNetworkType,
  ): WatchingWalletDescriptor {
    return WatchingWalletDescriptor(
      identifier = "WatchingWallet $localId",
      networkType = networkType,
      receivingDescriptor = bitcoinMultiSigDescriptorBuilder
        .watchingReceivingDescriptor(
          appPublicKey = appKey.key,
          hardwareKey = hardwareKey.key,
          serverKey = f8eSpendingKeyset.spendingPublicKey.key
        ),
      changeDescriptor = bitcoinMultiSigDescriptorBuilder
        .watchingChangeDescriptor(
          appPublicKey = appKey.key,
          hardwareKey = hardwareKey.key,
          serverKey = f8eSpendingKeyset.spendingPublicKey.key
        )
    )
  }
}
