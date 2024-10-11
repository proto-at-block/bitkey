package build.wallet.money.exchange

import kotlinx.coroutines.flow.StateFlow

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
}
