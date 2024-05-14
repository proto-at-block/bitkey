package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the customer's preferred fiat currency that they select through the app's settings.
 */
interface FiatCurrencyPreferenceRepository {
  /**
   * Emits latest stored [FiatCurrency] preference value, always starting with USD as a default
   * value while loading the preference from the database or initializing it for the first time
   * based on the device's locale.
   */
  val fiatCurrencyPreference: StateFlow<FiatCurrency>

  /**
   * Updates the persisted [FiatCurrency] preference.
   */
  suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error>

  /**
   * Clears the persisted [FiatCurrency] preference.
   */
  suspend fun clear(): Result<Unit, Error>
}
