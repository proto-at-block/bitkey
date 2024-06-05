package build.wallet.inappsecurity

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * A preference for storing whether one has enabled the biometric security preference
 */
interface BiometricPreference {
  /**
   * Get the current value of the preference
   */
  suspend fun get(): Result<Boolean, DbError>

  /**
   * Update the value of the preference
   */
  suspend fun set(enabled: Boolean): Result<Unit, DbError>

  /**
   * A flow of values of the current state of the preference
   */
  fun isEnabled(): Flow<Boolean>

  /**
   * Clear the value of the preference
   */
  suspend fun clear(): Result<Unit, DbError>
}
