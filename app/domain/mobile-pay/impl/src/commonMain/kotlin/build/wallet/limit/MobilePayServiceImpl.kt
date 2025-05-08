@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.limit

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.account.getAccount
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.coroutines.flow.tickerFlow
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClient
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClient
import build.wallet.limit.DailySpendingLimitStatus.MobilePayAvailable
import build.wallet.limit.DailySpendingLimitStatus.RequiresHardware
import build.wallet.limit.MobilePayData.*
import build.wallet.limit.MobilePayStatus.MobilePayDisabled
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class MobilePayServiceImpl(
  private val eventTracker: EventTracker,
  private val spendingLimitDao: SpendingLimitDao,
  private val spendingLimitF8eClient: MobilePaySpendingLimitF8eClient,
  private val mobilePayStatusRepository: MobilePayStatusRepository,
  private val appSessionManager: AppSessionManager,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val mobilePaySigningF8eClient: MobilePaySigningF8eClient,
  private val mobilePaySyncFrequency: MobilePaySyncFrequency,
  private val exchangeRateService: ExchangeRateService,
) : MobilePayService, MobilePayBalanceSyncWorker {
  override val mobilePayData = MutableStateFlow<MobilePayData?>(null)

  override suspend fun executeWork() {
    coroutineScope {
      launch {
        // Keep our in-memory cache of mobile pay data up to date by subscribing to
        // the mobile pay status repository
        accountService.accountStatus()
          .flatMapLatest { accountStatusResult ->
            val accountStatus = accountStatusResult.get()
            if (accountStatus is AccountStatus.ActiveAccount && accountStatus.account is FullAccount) {
              combine(
                mobilePayStatusRepository.status(accountStatus.account as FullAccount),
                fiatCurrencyPreferenceRepository.fiatCurrencyPreference,
                exchangeRateService.exchangeRates
              ) { mobilePayStatus, fiatCurrency, exchangeRates ->
                mobilePayStatus.toMobilePayData(fiatCurrency, exchangeRates)
              }
            } else {
              // If we don't have an active full account, mobile pay data is just null
              flowOf(null)
            }
          }.collect(mobilePayData)
      }

      // periodically sync mobile pay status from f8e
      launch {
        bitcoinWalletService.transactionsData().collectLatest { transactions ->
          if (transactions != null) {
            tickerFlow(mobilePaySyncFrequency.value)
              .filter { appSessionManager.isAppForegrounded() }
              .collectLatest {
                mobilePayStatusRepository.refreshStatus()
              }
          }
        }
      }
    }
  }

  override suspend fun deleteLocal(): Result<Unit, Error> {
    return spendingLimitDao.removeAllLimits()
  }

  override suspend fun signPsbtWithMobilePay(psbt: Psbt): Result<Psbt, Error> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      mobilePaySigningF8eClient.signWithSpecificKeyset(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        keysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
        psbt = psbt
      ).bind()
    }

  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
  ): DailySpendingLimitStatus {
    // If F8e did not return a current balance, we fall back to requiring hardware
    return when (val data = mobilePayData.value) {
      null -> RequiresHardware
      else -> when (data) {
        is MobilePayDisabledData -> RequiresHardware
        is MobilePayEnabledData -> {
          val remainingBitcoinAmount = data.remainingBitcoinSpendingAmount
          if (remainingBitcoinAmount != null && transactionAmount <= remainingBitcoinAmount) {
            MobilePayAvailable
          } else {
            RequiresHardware
          }
        }
      }
    }
  }

  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinTransactionSendAmount,
  ): DailySpendingLimitStatus {
    val amount = when (transactionAmount) {
      is BitcoinTransactionSendAmount.ExactAmount -> transactionAmount.money
      BitcoinTransactionSendAmount.SendAll -> {
        when (val data = bitcoinWalletService.transactionsData().value) {
          null -> return RequiresHardware
          else -> data.balance.total
        }
      }
    }

    return getDailySpendingLimitStatus(amount)
  }

  override suspend fun disable(): Result<Unit, Error> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()
      // TODO (W-4166): Currently, if the F8e request fails, we do not handle the error at all.
      //  We should handle that so F8e/App should _never_ be out of sync
      spendingLimitF8eClient.disableMobilePay(account.config.f8eEnvironment, account.accountId)

      spendingLimitDao.disableSpendingLimit()
        .bind()

      eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    }

  override suspend fun setLimit(
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()
      spendingLimitF8eClient
        .setSpendingLimit(
          fullAccountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          limit = spendingLimit,
          hwFactorProofOfPossession = hwFactorProofOfPossession
        )
        .logNetworkFailure { "Unable to save limit to the backend" }
        .bind()

      // save new active limit to local database
      spendingLimitDao
        .saveAndSetSpendingLimit(limit = spendingLimit)
        .logFailure { "Failed to update local limit database" }
        .bind()

      eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    }
  }

  private fun MobilePayStatus.toMobilePayData(
    fiatCurrency: FiatCurrency,
    exchangeRates: List<ExchangeRate>,
  ) = when (this) {
    is MobilePayDisabled -> MobilePayDisabledData(mostRecentSpendingLimit)
    is MobilePayEnabled -> MobilePayEnabledData(
      activeSpendingLimit = convertSpendingLimitToCurrency(
        activeSpendingLimit,
        fiatCurrency,
        exchangeRates
      ),
      remainingBitcoinSpendingAmount = balance?.available,
      remainingFiatSpendingAmount = balance?.available?.let {
        remainingFiatSpendingAmount(it, fiatCurrency, exchangeRates)
      }
    )
  }

  /**
   * Converts the server-provided spending limit into the user's local currency, if they differ.
   *
   * F8e persists the user's original currency preference when setting a mobile pay spending limit.
   * However, the user can later change their currency preference (e.g. when traveling), and we want
   * mobile pay to continue working. Yet, we don't want to require the user to have to update their
   * mobile pay settings as currency is an *appearance*, so we convert to the current currency preference.
   * locally.
   *
   * Returns null if currency conversion fails.
   */
  private fun convertSpendingLimitToCurrency(
    spendingLimit: SpendingLimit,
    fiatCurrency: FiatCurrency,
    exchangeRates: List<ExchangeRate>,
  ): SpendingLimit? {
    if (spendingLimit.amount.currency == fiatCurrency) return spendingLimit

    // First, convert the spending limit fiat into BTC.
    val btcAmount = currencyConverter.convert(spendingLimit.amount, BTC, exchangeRates)

    // Then, convert it into the user's currency preference.
    return btcAmount?.let {
      currencyConverter.convert(btcAmount, fiatCurrency, exchangeRates) as FiatMoney
    }?.let { spendingLimit.copy(amount = it) }
  }

  /**
   * Calculates the remaining spending amount in the user's preferred currency.
   */
  private fun remainingFiatSpendingAmount(
    remainingBitcoinMoney: BitcoinMoney,
    fiatCurrency: FiatCurrency,
    exchangeRates: List<ExchangeRate>,
  ): FiatMoney? {
    return currencyConverter.convert(
      remainingBitcoinMoney, fiatCurrency, exchangeRates
    ) as FiatMoney?
  }
}
