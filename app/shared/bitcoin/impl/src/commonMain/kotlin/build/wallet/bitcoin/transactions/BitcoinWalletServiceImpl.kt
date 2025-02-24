@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.bitcoin.transactions

import build.wallet.LoadableValue.LoadedValue
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.*
import build.wallet.asLoadableValue
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinWalletServiceImpl.Balances.LoadedBalance
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.bitcoin.wallet.CoinSelectionStrategy
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class BitcoinWalletServiceImpl(
  private val currencyConverter: CurrencyConverter,
  private val accountService: AccountService,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val appSessionManager: AppSessionManager,
  private val outgoingTransactionDetailDao: OutgoingTransactionDetailDao,
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val exchangeRateService: ExchangeRateService,
  private val feeRateEstimator: BitcoinFeeRateEstimator,
  private val bitcoinWalletSyncFrequency: BitcoinWalletSyncFrequency,
) : BitcoinWalletService, BitcoinWalletSyncWorker {
  private val spendingWallet = MutableStateFlow<SpendingWallet?>(null)
  private val transactionsData = MutableStateFlow<TransactionsData?>(null)
  private val exchangeRates =
    exchangeRateService.exchangeRates
      .mapLatest { exchangeRates ->
        val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
        exchangeRates.filter {
          it.toCurrency == fiatCurrency.textCode || it.fromCurrency == fiatCurrency.textCode
        }
      }
      .distinctUntilChanged()

  private var periodicSyncJob: Job? = null

  override suspend fun executeWork() {
    coroutineScope {
      launch {
        accountService.accountStatus()
          .mapLatest { it.get().toSpendingKeyset() }
          .distinctUntilChanged() // If the keyset hasn't changed, neither has the wallet.
          .flatMapLatest { keys ->
            val wallet = keys?.let { appSpendingWalletProvider.getSpendingWallet(keys).get() }
            spendingWallet.emit(wallet)

            // Cancel any existing periodic sync
            periodicSyncJob?.cancel()

            // Make sure to initialize the balance and transactions before the sync occurs so that
            // the call to sync doesn't block fetching the balance and transactions
            wallet?.initializeBalanceAndTransactions()

            // Also, launch a period sync for the spending wallet (whenever one is available).
            periodicSyncJob = wallet?.launchPeriodicSync(
              scope = this,
              interval = bitcoinWalletSyncFrequency.value
            )

            wallet.toTransactionsData()
          }.collect(transactionsData)
      }

      launch {
        // Manually sync whenever the app is foregrounded to ensure we're as up to date as possible.
        combine(
          spendingWallet,
          appSessionManager.appSessionState
        ) { wallet, appSessionState ->
          if (wallet != null && appSessionState == AppSessionState.FOREGROUND) {
            wallet.sync()
          }
        }.collectLatest {}
      }
    }
  }

  override fun spendingWallet() = spendingWallet

  override fun transactionsData() = transactionsData

  override suspend fun sync(): Result<Unit, Error> {
    return spendingWallet.value?.sync() ?: Ok(Unit)
  }

  private fun SpendingWallet?.toTransactionsData(): Flow<TransactionsData?> {
    if (this == null) return flowOf(null)

    return combine(
      balances(this),
      transactions().asLoadableValue(),
      unspentOutputs().asLoadableValue()
    ) { balance, transactions, unspentOutputs ->
      if (balance is LoadedBalance && transactions is LoadedValue && unspentOutputs is LoadedValue) {
        val utxos = groupUtxos(
          allUtxos = unspentOutputs.value,
          transactions = transactions.value
        )

        utxos.fold(
          success = {
            TransactionsData(
              balance = balance.bitcoinBalance,
              transactions = transactions.value.toImmutableList(),
              utxos = it,
              fiatBalance = balance.fiatBalance
            )
          },
          failure = { null }
        )
      } else {
        null
      }
    }
  }

  /**
   * Returns wallet's UTXOs, grouped by confirmation status.
   */
  private suspend fun groupUtxos(
    allUtxos: List<BdkUtxo>,
    transactions: List<BitcoinTransaction>,
  ): Result<Utxos, Error> =
    coroutineBinding<Utxos, Error> {
      // Use Default dispatcher for filtering and grouping UTXOs.
      withContext(Dispatchers.Default) {
        // IDs of wallet's confirmed transactions.
        val confirmedTransactionIds = transactions
          .filter { it.confirmationStatus is Confirmed }
          .map { tx -> tx.id }
          .toSet()

        // Group UTXOs by confirmation status.
        val (confirmedUtxos, unconfirmedUtxos) = allUtxos
          .partition { utxo -> utxo.outPoint.txid in confirmedTransactionIds }

        Utxos(
          confirmed = confirmedUtxos.toSet(),
          unconfirmed = unconfirmedUtxos.toSet()
        )
      }
    }.logFailure { "Error loading wallet UTXOs." }

  private fun AccountStatus?.toSpendingKeyset() =
    when (this) {
      is ActiveAccount -> getSpendingKeysetForAccount(account)
      is OnboardingAccount, is LiteAccountUpgradingToFullAccount, NoAccount, null -> null
    }

  private fun getSpendingKeysetForAccount(account: Account) =
    when (account) {
      is FullAccount -> account.keybox.activeSpendingKeyset
      else -> null
    }

  private fun balances(spendingWallet: SpendingWallet) =
    combine(
      spendingWallet.balance().asLoadableValue(),
      // Recalculate if currency changes
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference,
      // Recalculate if the exchange rate for the fiatCurrency changes
      exchangeRates
    ) { bitcoinBalance, fiatCurrency, _ ->
      if (bitcoinBalance is LoadedValue) {
        LoadedBalance(
          bitcoinBalance = bitcoinBalance.value,
          fiatBalance = bitcoinBalance.fiatBalance(fiatCurrency)
        )
      } else {
        Balances.LoadingBalance
      }
    }
      .distinctUntilChanged()

  private suspend fun LoadedValue<BitcoinBalance>.fiatBalance(fiatCurrency: FiatCurrency) =
    currencyConverter
      .convert(
        fromAmount = this.value.total,
        toCurrency = fiatCurrency,
        atTime = null
      ).firstOrNull() as? FiatMoney

  override suspend fun broadcast(
    psbt: Psbt,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<BroadcastDetail, Error> =
    coroutineBinding {
      val broadcastDetail = bitcoinBlockchain.broadcast(psbt = psbt).bind()

      // When we successfully broadcast the transaction, store the transaction details and
      // exchange rate.
      val exchangeRates = exchangeRateService.exchangeRates.value
      val estimatedConfirmationTime =
        broadcastDetail.broadcastTime + estimatedTransactionPriority.toDuration()
      outgoingTransactionDetailDao
        .insert(
          broadcastTime = broadcastDetail.broadcastTime,
          transactionId = broadcastDetail.transactionId,
          estimatedConfirmationTime = estimatedConfirmationTime,
          exchangeRates = exchangeRates
        )
        .logFailure { "Error persisting outgoing transaction and its exchange rates." }
        .bind()

      sync().bind()

      broadcastDetail
    }.logFailure { "Error broadcasting transaction" }

  override suspend fun createPsbtsForSendAmount(
    sendAmount: BitcoinTransactionSendAmount,
    recipientAddress: BitcoinAddress,
  ): Result<Map<EstimatedTransactionPriority, Psbt>, Error> =
    coroutineBinding {
      val wallet = spendingWallet.value
      ensureNotNull(wallet) { Error("No spending wallet found.") }

      val balance = when (val data = transactionsData.value) {
        null -> null
        else -> data.balance
      }
      ensureNotNull(balance) { Error("No balance available") }

      val feeRates = feeRateEstimator.getEstimatedFeeRates(wallet.networkType)
        .bind()

      // Build the slowest psbt we support
      val sixtyMinutesPsbt = wallet.createSignedPsbt(
        constructionType = SpendingWallet.PsbtConstructionMethod.Regular(
          recipientAddress = recipientAddress,
          amount = sendAmount,
          feePolicy = FeePolicy.Rate(feeRate = feeRates.hourFeeRate),
          coinSelectionStrategy = CoinSelectionStrategy.Default
        )
      ).mapError { Error("Error creating PSBT for 60 minutes") }
        .bind()

      // BDK's default coin selection isn't deterministic so we need to use the inputs from the
      // previous PSBTs to ensure that the same inputs are used for the next PSBTs.

      // Build the next fastest psbt using the outpoints of the previous psbt
      val thirtyMinutesPsbt = wallet.createSignedPsbt(
        constructionType = SpendingWallet.PsbtConstructionMethod.Regular(
          recipientAddress = recipientAddress,
          amount = sendAmount,
          feePolicy = FeePolicy.Rate(feeRate = feeRates.halfHourFeeRate),
          coinSelectionStrategy = CoinSelectionStrategy.Preselected(sixtyMinutesPsbt.inputs)
        )
      ).getOrElse {
        return@coroutineBinding mapOf(EstimatedTransactionPriority.SIXTY_MINUTES to sixtyMinutesPsbt)
      }

      // Build the fastest psbt using the outpoints of the previous psbt
      val fastestPsbt = wallet.createSignedPsbt(
        constructionType = SpendingWallet.PsbtConstructionMethod.Regular(
          recipientAddress = recipientAddress,
          amount = sendAmount,
          feePolicy = FeePolicy.Rate(feeRate = feeRates.fastestFeeRate),
          coinSelectionStrategy = CoinSelectionStrategy.Preselected(thirtyMinutesPsbt.inputs)
        )
      ).getOrElse {
        return@coroutineBinding mapOf(
          EstimatedTransactionPriority.SIXTY_MINUTES to sixtyMinutesPsbt,
          EstimatedTransactionPriority.THIRTY_MINUTES to thirtyMinutesPsbt
        )
      }

      mapOf(
        EstimatedTransactionPriority.SIXTY_MINUTES to sixtyMinutesPsbt,
        EstimatedTransactionPriority.THIRTY_MINUTES to thirtyMinutesPsbt,
        EstimatedTransactionPriority.FASTEST to fastestPsbt
      )
    }

  /**
   * A simple private wrapper around bitcoin and fiat balances.
   */
  private sealed interface Balances {
    data object LoadingBalance : Balances

    data class LoadedBalance(
      val bitcoinBalance: BitcoinBalance,
      val fiatBalance: FiatMoney?,
    ) : Balances
  }
}
