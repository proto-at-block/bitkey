package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages caching the value from [FiatCurrencyPreferenceDao] in memory.
 */
interface FiatCurrencyPreferenceRepository {
  /**
   * Emits latest default [FiatCurrency] value, updated by [launchSync].
   *
   * This should only be used as a fallback if there is not a currency explicitly set by
   * the customer (i.e. if [fiatCurrencyPreference] returns null).
   */
  val defaultFiatCurrency: StateFlow<FiatCurrency>

  /**
   * Emits latest stored [FiatCurrency] preference value, updated by [launchSync]
   * or [setFiatCurrencyPreference].
   *
   * This takes priority over / overrides [defaultFiatCurrency].
   */
  val fiatCurrencyPreference: StateFlow<FiatCurrency?>

  /**
   * Launches a non-blocking coroutine to continuously sync latest local [FiatCurrency]
   * values into [defaultFiatCurrency] and [fiatCurrencyPreference].
   *
   * This function should be called only once.
   */
  fun launchSync(scope: CoroutineScope)

  /**
   * Updates the persisted [FiatCurrency] preference.
   */
  suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency)

  /**
   * Clears the persisted [FiatCurrency] preference.
   */
  suspend fun clear(): Result<Unit, Error>
}
