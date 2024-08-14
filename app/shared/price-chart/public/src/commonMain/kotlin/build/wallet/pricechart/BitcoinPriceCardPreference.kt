package build.wallet.pricechart

import build.wallet.db.DbError
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
  suspend fun get(): Result<Boolean, DbError>

  /**
   * Update the current value of the preference
   */
  suspend fun set(enabled: Boolean): Result<Unit, DbError>

  /**
   * Clear the value of the preference
   */
  suspend fun clear(): Result<Unit, DbError>
}
