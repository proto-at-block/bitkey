package build.wallet.pricechart

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * The preference is used to store the selected time scale for the chart, which can be updated in
 * app appearance settings. The value defaults to [ChartTimeScale.DAY] if it has not been set.
 */
interface ChartRangePreference {
  /**
   * Retrieve the current value of the preference as a StateFlow with the latest
   */
  val selectedRange: StateFlow<ChartRange>

  /**
   * Retrieve the current value of the preference
   */
  suspend fun get(): Result<ChartRange, Error>

  /**
   * Update the current value of the preference
   */
  suspend fun set(scale: ChartRange): Result<Unit, Error>

  /**
   * Clear the value of the preference
   */
  suspend fun clear(): Result<Unit, Error>
}
