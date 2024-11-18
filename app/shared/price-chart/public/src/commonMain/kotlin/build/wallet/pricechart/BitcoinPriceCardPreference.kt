package build.wallet.pricechart

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

interface BitcoinPriceCardPreference {
  /**
   * Retrieve the current value of the preference as a StateFlow with the latest
   */
  val isEnabled: StateFlow<Boolean>

  /**
   * Retrieve the current value of the preference
   */
  suspend fun get(): Result<Boolean, Error>

  /**
   * Update the current value of the preference
   */
  suspend fun set(enabled: Boolean): Result<Unit, Error>

  /**
   * Clear the value of the preference
   */
  suspend fun clear(): Result<Unit, Error>
}
