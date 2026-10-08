package build.wallet.money.exchange

import bitkey.account.AccountConfigService
import build.wallet.account.AccountService
import build.wallet.activity.TransactionActivityOperations
import build.wallet.bitkey.account.LiteAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.Currency
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.worker.RefreshOperationFilter
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import build.wallet.worker.TimeoutStrategy
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
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
  private val appVariant: AppVariant,
  appSessionManager: AppSessionManager,
  exchangeRateSyncFrequency: ExchangeRateSyncFrequency,
  appScope: CoroutineScope,
) : ExchangeRateService, ExchangeRateSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.OnEvent(
      observer = appSessionManager.appSessionState
        .filter { it == AppSessionState.FOREGROUND }
    ),
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
   *
   * Only rates fetched from the active f8e environment are emitted. Rates are stored
   * with the environment that produced them, and different environments serve
   * different prices, so a rate from any other environment is not a staler version of
   * the same price — it is the wrong price. Filtering here rather than at each call
   * site means every consumer (send, spending limits, balances, transaction metadata)
   * is covered, including across the window between an environment switch and the
   * first sync against the new environment.
   */
  override val exchangeRates: StateFlow<List<ExchangeRate>> =
    exchangeRateDao.allExchangeRatesWithEnvironment()
      .combine(activeF8eEnvironment()) { rates, environment ->
        when (environment) {
          null -> emptyList()
          else -> rates.filter { it.f8eEnvironment == environment }.map { it.exchangeRate }
        }
      }
      .distinctUntilChanged()
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
    activeF8eEnvironment().first()?.run {
      invalidateRatesFromOtherEnvironments(this)
      updateExchangeRates(this)
    }
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

  override suspend fun syncRates(): Result<List<ExchangeRate>, Error> {
    val f8eEnvironment = activeF8eEnvironment().first()
      ?: return Ok(emptyList()) // No environment means no account that needs rates
    invalidateRatesFromOtherEnvironments(f8eEnvironment)
    return exchangeRateF8eClient.getExchangeRates(f8eEnvironment)
      .map { rates ->
        rates.forEach { exchangeRateDao.storeExchangeRate(it, f8eEnvironment) }
        rates // Return the freshly fetched rates
      }
      .mapError { Error("Failed to sync exchange rates", it) }
  }

  override suspend fun clearRates() {
    check(appVariant != Customer) { "Not allowed in Customer builds." }
    exchangeRateDao.clear()
  }

  /**
   * Drops locally cached rates that did not come from [environment], so rates fetched
   * from a previous environment are never used to convert amounts against the current
   * one.
   *
   * Provenance is read from storage rather than tracked in memory, so this also covers
   * a switch followed by a process restart: rate rows persist across launches, and an
   * in-memory marker would come back empty and let them through.
   */
  private suspend fun invalidateRatesFromOtherEnvironments(environment: F8eEnvironment) {
    exchangeRateDao.deleteRatesNotFrom(environment)
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
        rates.forEach { exchangeRateDao.storeExchangeRate(it, f8eEnvironment) }
      }
  }

  private fun activeF8eEnvironment(): Flow<F8eEnvironment?> {
    // Combined rather than reading defaultConfig().value inside map: with no active
    // account, activeAccount() emits null once and does not re-emit, so a snapshot read
    // would never observe a later change to the default environment (which is how the
    // environment is switched during onboarding, before an account exists).
    return combine(
      accountService.activeAccount(),
      accountConfigService.defaultConfig()
    ) { account, defaultConfig ->
      when (account) {
        // Lite Accounts do not require exchange rates as they have no funds/never see fiat amounts:
        is LiteAccount -> null
        // When there is no account, sync rates with the default config, as it might be needed for recovery steps:
        null -> defaultConfig.f8eEnvironment
        else -> account.config.f8eEnvironment
      }
    }.distinctUntilChanged()
  }

  private fun List<ExchangeRate>.timeRetrievedForCurrency(currency: Currency): Instant? {
    return firstOrNull { it.fromCurrency == currency.textCode || it.toCurrency == currency.textCode }
      ?.timeRetrieved
  }
}
