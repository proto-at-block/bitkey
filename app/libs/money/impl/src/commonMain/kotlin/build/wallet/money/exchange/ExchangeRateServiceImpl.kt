package build.wallet.money.exchange

import bitkey.account.AccountConfigService
import build.wallet.account.AccountService
import build.wallet.activity.TransactionActivityOperations
import build.wallet.bitkey.account.LiteAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.Currency
import build.wallet.worker.RefreshOperationFilter
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import build.wallet.worker.TimeoutStrategy
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class ExchangeRateServiceImpl(
  private val exchangeRateDao: ExchangeRateDao,
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val clock: Clock,
  private val accountService: AccountService,
  private val accountConfigService: AccountConfigService,
  exchangeRateSyncFrequency: ExchangeRateSyncFrequency,
  appScope: CoroutineScope,
) : ExchangeRateService, ExchangeRateSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.Refresh(
      type = RefreshOperationFilter.Subset(TransactionActivityOperations)
    ),
    RunStrategy.Periodic(
      interval = exchangeRateSyncFrequency.value
    ),
    RunStrategy.OnEvent(
      observer = activeF8eEnvironment().filterNotNull().distinctUntilChanged()
    )
  )

  override val timeout: TimeoutStrategy = TimeoutStrategy.RefreshOnly(
    limit = 5.seconds
  )

  override val retryStrategy: RetryStrategy = RetryStrategy.Always(
    delay = 1.seconds,
    retries = 2
  )

  /**
   * Caches the latest exchange rates from the local database.
   */
  override val exchangeRates: StateFlow<List<ExchangeRate>> = exchangeRateDao.allExchangeRates()
    .stateIn(appScope, SharingStarted.Eagerly, emptyList())

  /**
   * Executes the sync of exchange rates.
   *
   * Updates the exchange rate DAO with the latest exchange rates from F8e,
   * when the app is in the foreground and the account is an eligible
   * full-account that requires exchange rates to sync.
   */
  override suspend fun executeWork() {
    // Cache latest active f8e environment
    activeF8eEnvironment().first()?.run { updateExchangeRates(this) }
  }

  override fun mostRecentRatesSinceDurationForCurrency(
    duration: Duration,
    currency: Currency,
  ): List<ExchangeRate>? {
    val instant = exchangeRates.value.timeRetrievedForCurrency(currency)
    return when {
      // if rates are older than duration or we cant find any for our fiat currency, we don't
      // use them
      instant == null || instant <= clock.now() - duration -> null
      else -> exchangeRates.value
    }
  }

  /**
   * Pulls latest exchange rates from given f8e environment and stores them
   * in the local database.
   */
  private suspend fun updateExchangeRates(f8eEnvironment: F8eEnvironment) {
    // Get exchange rates from F8e and store them all
    // Ignore any failures
    exchangeRateF8eClient.getExchangeRates(f8eEnvironment)
      .onSuccess { rates ->
        rates.forEach { exchangeRateDao.storeExchangeRate(it) }
      }
  }

  private fun activeF8eEnvironment(): Flow<F8eEnvironment?> {
    return accountService.activeAccount()
      .map {
        when (it) {
          // Lite Accounts do not require exchange rates as they have no funds/never see fiat amounts:
          is LiteAccount -> null
          // When there is no account, sync rates with the default config, as it might be needed for recovery steps:
          null -> accountConfigService.defaultConfig().value.f8eEnvironment
          else -> it.config.f8eEnvironment
        }
      }
  }

  private fun List<ExchangeRate>.timeRetrievedForCurrency(currency: Currency): Instant? {
    return firstOrNull { it.fromCurrency == currency.textCode || it.toCurrency == currency.textCode }
      ?.timeRetrieved
  }
}
