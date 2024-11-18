@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.money.exchange

import build.wallet.analytics.events.AppSessionManager
import build.wallet.analytics.events.AppSessionState
import build.wallet.f8e.F8eEnvironment
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.log
import build.wallet.money.currency.Currency
import build.wallet.time.Delayer.Default.delay
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ExchangeRateServiceImpl(
  private val exchangeRateDao: ExchangeRateDao,
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val appSessionManager: AppSessionManager,
  private val keyboxDao: KeyboxDao,
  private val clock: Clock,
) : ExchangeRateService, ExchangeRateSyncWorker {
  private val exchangeRatesCache = MutableStateFlow<List<ExchangeRate>>(emptyList())

  private val activeF8eEnvironmentState = MutableStateFlow<F8eEnvironment?>(null)

  /**
   * A shared flow that emits a [Unit] every time a sync is requested.
   * [executeWork] listens to this flow and performs a sync whenever it emits.
   */
  private val syncRequests = MutableSharedFlow<F8eEnvironment>(
    extraBufferCapacity = 1,
    onBufferOverflow = DROP_OLDEST
  )

  /**
   * Determines how frequently to sync exchange rates.
   */
  private val periodicSyncDelay = 1.minutes

  override val exchangeRates: StateFlow<List<ExchangeRate>> = exchangeRatesCache

  override suspend fun requestSync() {
    activeF8eEnvironmentState.value?.let { syncRequests.emit(it) }
  }

  /**
   * Executes the sync of exchange rates.
   * This function encompasses multiple responsibilities that coordinate to keep the exchange rates
   * up-to-date.
   *
   * Initially, it caches the latest active F8e environment by collecting it into
   * [activeF8eEnvironmentState]. This value is used to determine the environment from which to fetch
   * exchange rates.
   *
   * Next, it sets up another coroutine to emit sync requests whenever the active F8e environment changes.
   * This reactivity ensures that any change in the environment triggers a sync of exchange rates.
   *
   * In addition to reacting to environment changes, the function includes a periodic sync mechanism.
   * A coroutine runs in an infinite loop, emitting sync requests every [periodicSyncDelay].
   * This guarantees regular updates, maintaining the exchange rates.
   *
   * When a sync request is emitted (through [syncRequests]), another coroutine listens to these
   * requests and initiates the update process for exchange rates. The exchange rates are written
   * to the local database. However, it only proceeds with the update if the app is in the foreground.
   * We don't want to update exchange rates unnecessarily when the app is in the background.
   *
   * Finally, to provide latest exchange rates through [exchangeRates], a coroutine is launched to
   * continuously update the [exchangeRatesCache] with the most recent exchange rates from the local
   * database. This ensures that the application always has the latest data.
   */
  override suspend fun executeWork() {
    coroutineScope {
      // Cache latest active f8e environment
      launch {
        activeF8eEnvironment().collect(activeF8eEnvironmentState)
      }

      // Send request for sync whenever active f8e environment changes
      launch {
        combine(
          activeF8eEnvironmentState.filterNotNull(),
          appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
        ) { f8eEnvironment, _ -> f8eEnvironment }
          .collect { f8eEnvironment: F8eEnvironment ->
            syncRequests.emit(f8eEnvironment)
          }
      }

      // Send request for sync periodically
      launch {
        while (isActive) {
          activeF8eEnvironmentState.value?.let { f8eEnvironment ->
            syncRequests.emit(f8eEnvironment)
          }
          delay(duration = periodicSyncDelay)
        }
      }

      // On sync request, update exchange rates as long as app is foregrounded
      launch {
        syncRequests.collect { f8eEnvironment ->
          if (appSessionManager.isAppForegrounded()) {
            updateExchangeRates(f8eEnvironment)
          }
        }
      }

      // Update cache with latest exchange rates
      launch {
        exchangeRateDao.allExchangeRates().collect {
          exchangeRatesCache.value = it
        }
      }
    }
  }

  override fun mostRecentRatesSinceDurationForCurrency(
    duration: Duration,
    currency: Currency,
  ): List<ExchangeRate>? {
    val instant = exchangeRatesCache.value.timeRetrievedForCurrency(currency)
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
        log(Debug) { "Fetched exchange rates: $rates" }
        rates.forEach { exchangeRateDao.storeExchangeRate(it) }
      }
  }

  // TODO(W-9404): update exchange rates for other account types.
  private fun activeF8eEnvironment(): Flow<F8eEnvironment?> {
    return keyboxDao.activeKeybox().map { result ->
      result.get()?.config?.f8eEnvironment
    }
  }

  private fun List<ExchangeRate>.timeRetrievedForCurrency(currency: Currency): Instant? {
    return firstOrNull { it.fromCurrency == currency.textCode || it.toCurrency == currency.textCode }
      ?.timeRetrieved
  }
}
