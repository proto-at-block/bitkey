package build.wallet.money.exchange

import build.wallet.analytics.events.AppSessionManager
import build.wallet.f8e.ActiveF8eEnvironmentRepository
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.log
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

class ExchangeRateSyncerImpl(
  private val exchangeRateDao: ExchangeRateDao,
  private val f8eExchangeRateService: F8eExchangeRateService,
  private val activeF8eEnvironmentRepository: ActiveF8eEnvironmentRepository,
  private val appSessionManager: AppSessionManager,
) : ExchangeRateSyncer {
  private val internalFlow = MutableStateFlow<List<ExchangeRate>>(emptyList())

  override val exchangeRates: StateFlow<List<ExchangeRate>>
    get() = internalFlow.asStateFlow()

  /**
   * Launches non blocking coroutine and keeps syncing exchange rates as long as [CoroutineScope] is
   * alive.
   */
  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
  ) {
    scope.launch {
      syncTicker(delay = syncFrequency)
        .flatMapLatest {
          updateExchangeRates()
          exchangeRateDao.allExchangeRates()
        }.collect(internalFlow)
    }
  }

  /**
   * [Flow] that emits [Unit] every [delay].
   */
  private fun syncTicker(delay: Duration) =
    flow {
      while (currentCoroutineContext().isActive) {
        if (appSessionManager.isAppForegrounded()) {
          emit(Unit)
        }
        delay(delay)
      }
    }

  private suspend fun updateExchangeRates() {
    activeF8eEnvironmentRepository.activeF8eEnvironment()
      .first()
      .onSuccess { f8eEnvironment ->
        f8eEnvironment?.let {
          // Get exchange rates from F8e and store them all
          // Ignore any failures
          f8eExchangeRateService.getExchangeRates(f8eEnvironment)
            .onSuccess { rates ->
              log(Debug) { "Fetched exchange rates: $rates" }
              rates.forEach { exchangeRateDao.storeExchangeRate(it) }
            }
        }
      }
  }
}
