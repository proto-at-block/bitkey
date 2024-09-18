@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.limit

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.AppSessionManager
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitcoin.transactions.TransactionsData.TransactionsLoadedData
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClient
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayStatus.MobilePayDisabled
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.minutes

class MobilePayServiceImpl(
  private val eventTracker: EventTracker,
  private val spendingLimitDao: SpendingLimitDao,
  private val spendingLimitF8eClient: MobilePaySpendingLimitF8eClient,
  private val mobilePayStatusRepository: MobilePayStatusRepository,
  private val appSessionManager: AppSessionManager,
  private val transactionsService: TransactionsService,
  private val accountService: AccountService,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : MobilePayService, MobilePayBalanceSyncWorker {
  private val syncTicker =
    flow {
      while (currentCoroutineContext().isActive) {
        if (appSessionManager.isAppForegrounded()) {
          emit(Unit)
        }
        delay(30.minutes)
      }
    }

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
              mobilePayStatusRepository.status(accountStatus.account as FullAccount)
                .combine(
                  // When the currency changes, we need to recompute the remaining fiat balance
                  fiatCurrencyPreferenceRepository.fiatCurrencyPreference
                ) { mobilePayStatus, fiatCurrency ->
                  mobilePayStatus.toMobilePayData(fiatCurrency)
                }
            } else {
              // If we don't have an active full account, mobile pay data is just null
              flowOf(null)
            }
          }.collect(mobilePayData)
      }

      // Set up a periodic syncer that syncs from f8e to our local db.
      launch {
        combine(
          // Refresh mobile pay status on every transaction update.
          transactionsService.transactionsData(),
          // Refresh mobile pay status every 30 minutes, so long as we have a loaded wallet.
          syncTicker
        ) { transactionsData, _ ->
          if (transactionsData is TransactionsLoadedData) {
            mobilePayStatusRepository.refreshStatus()
          }
        }.collect()
      }
    }
  }

  override suspend fun deleteLocal(): Result<Unit, Error> {
    return spendingLimitDao.removeAllLimits()
  }

  override suspend fun disable(account: FullAccount): Result<Unit, Error> =
    coroutineBinding {
      // TODO (W-4166): Currently, if the F8e request fails, we do not handle the error at all.
      //  We should handle that so F8e/App should _never_ be out of sync
      spendingLimitF8eClient.disableMobilePay(account.config.f8eEnvironment, account.accountId)

      spendingLimitDao.disableSpendingLimit()
        .bind()

      eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    }

  override suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return coroutineBinding {
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

  private suspend fun MobilePayStatus.toMobilePayData(fiatCurrency: FiatCurrency) =
    when (this) {
      is MobilePayDisabled -> MobilePayDisabledData(mostRecentSpendingLimit)
      is MobilePayEnabled -> MobilePayEnabledData(
        activeSpendingLimit = activeSpendingLimit,
        balance = balance,
        remainingFiatSpendingAmount = remainingFiatSpendingAmount(
          mobilePayBalance = balance,
          fiatCurrency = fiatCurrency
        )
      )
    }

  private suspend fun remainingFiatSpendingAmount(
    mobilePayBalance: MobilePayBalance?,
    fiatCurrency: FiatCurrency,
  ): FiatMoney? {
    return mobilePayBalance?.let { balance ->
      currencyConverter.convert(balance.spent, fiatCurrency, null).first()
        ?.let { spentMoneyInFiat ->
          val balanceLimit = balance.limit.amount
          FiatMoney(fiatCurrency, balanceLimit.value - spentMoneyInFiat.value)
        }
    }
  }
}
