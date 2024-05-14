package build.wallet.inappsecurity

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * A preference for storing whether one has enabled the hide balance by default preference
 */
interface HideBalancePreference {
  /**
   * Retrieve the current value of the preference
   */
  suspend fun get(): Result<Boolean, DbError>

  /**
   * Update the current value of the preference
   */
  suspend fun set(enabled: Boolean): Result<Unit, DbError>

  /**
   * Retrieve the current value of the preference as a flow with the latest
   */
  fun isEnabled(): Flow<Boolean>
}
