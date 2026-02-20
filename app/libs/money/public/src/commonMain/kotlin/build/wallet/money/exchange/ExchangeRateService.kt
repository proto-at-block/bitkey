package build.wallet.money.exchange

import build.wallet.money.currency.Currency
import com.github.michaelbull.result.Result
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

  fun mostRecentRatesSinceDurationForCurrency(
    duration: Duration,
    currency: Currency,
  ): List<ExchangeRate>?

  /**
   * Triggers an immediate sync of exchange rates from F8e.
   * Returns the freshly synced rates on success, or error if the sync failed.
   * The returned rates are also stored locally for future use.
   */
  suspend fun syncRates(): Result<List<ExchangeRate>, Error>

  /**
   * Clears all exchange rates from local storage.
   * Debug/testing only - will throw in Customer builds.
   */
  suspend fun clearRates()
}
