package build.wallet.money.exchange

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ExchangeRateSyncer {
  /**
   * Emits latest local [ExchangeRate] value, updated by [launchSync]
   */
  val exchangeRates: StateFlow<List<ExchangeRate>>

  /**
   * Launches a non-blocking coroutine which will pull latest exchange rates (currently BTC:USD) as
   * frequently as [syncFrequency] and update local cache of exchanges.
   *
   * For now, it's recommended to launch single [launchSync] coroutine instance at the time (for
   * example in the top level app state machine). It's not currently optimized to be called multiple
   * times.
   *
   * Use [CurrencyConverter] to convert amounts using latest exchange rate.
   */
  fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration = 15.seconds,
  )
}
