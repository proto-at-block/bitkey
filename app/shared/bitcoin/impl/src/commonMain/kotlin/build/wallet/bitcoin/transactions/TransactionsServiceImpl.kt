@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.bitcoin.transactions

import build.wallet.LoadableValue.LoadedValue
import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.account.AccountStatus.*
import build.wallet.analytics.events.AppSessionManager
import build.wallet.analytics.events.AppSessionState
import build.wallet.asLoadableValue
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.transactions.TransactionsData.LoadingTransactionsData
import build.wallet.bitcoin.transactions.TransactionsData.TransactionsLoadedData
import build.wallet.bitcoin.transactions.TransactionsServiceImpl.Balances.LoadedBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRateService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class TransactionsServiceImpl(
  private val currencyConverter: CurrencyConverter,
  private val accountRepository: AccountRepository,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val appSessionManager: AppSessionManager,
  exchangeRateService: ExchangeRateService,
) : TransactionsService, TransactionSyncWorker {
  private val spendingWallet = MutableStateFlow<SpendingWallet?>(null)
  private val transactionsData = MutableStateFlow<TransactionsData>(LoadingTransactionsData)
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
        accountRepository.accountStatus()
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
              interval = 10.seconds
            )

            wallet.toTransactionsData()
          }.collect(transactionsData)
      }

      launch {
        // Manually sync whenever the app is foregrounded to ensure we're as up to date as possible.
        appSessionManager.appSessionState
          .collect { appSessionState ->
            if (appSessionState == AppSessionState.FOREGROUND) {
              spendingWallet.value?.sync()
            }
          }
      }
    }
  }

  override fun spendingWallet() = spendingWallet

  override fun transactionsData() = transactionsData

  override suspend fun syncTransactions(): Result<Unit, Error> {
    return spendingWallet.value?.sync() ?: Ok(Unit)
  }

  private fun SpendingWallet?.toTransactionsData(): Flow<TransactionsData> {
    if (this == null) return flowOf(LoadingTransactionsData)

    return combine(
      balances(this),
      transactions().asLoadableValue(),
      unspentOutputs().asLoadableValue()
    ) { balance, transactions, unspentOutputs ->
      if (balance is LoadedBalance && transactions is LoadedValue && unspentOutputs is LoadedValue) {
        TransactionsLoadedData(
          balance = balance.bitcoinBalance,
          transactions = transactions.value.toImmutableList(),
          unspentOutputs = unspentOutputs.value.toImmutableList(),
          fiatBalance = balance.fiatBalance
        )
      } else {
        LoadingTransactionsData
      }
    }
  }

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
