package build.wallet.money.exchange

import build.wallet.money.currency.Currency
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Domain service for managing exchange rates. The rates are synced by the [ExchangeRateSyncWorker].
 */
interface ExchangeRateService {
  /**
   * Emits latest local [ExchangeRate] value, periodically updated by [ExchangeRateSyncWorker].
   */
  val exchangeRates: StateFlow<List<ExchangeRate>>

  /**
   * Requests a sync of remote exchange rates.
   */
  suspend fun requestSync()

  fun mostRecentRatesSinceDurationForCurrency(
    duration: Duration,
    currency: Currency,
  ): List<ExchangeRate>?
}
