package build.wallet.recovery

import build.wallet.db.DbTransactionError
import build.wallet.f8e.recovery.ServerRecovery
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Dao used to interact with all Recovery
 * related entities
 */
interface RecoveryDao {
  fun activeRecovery(): Flow<Result<Recovery, Error>>

  /**
   * Sets the [ServerRecovery] that will be stored on the App
   * and the [appAuthKey] and [hardwareAuthKey] that will be used
   * for the Recovery, if the auth keys do not match with is on
   * the [ServerRecovery] we will throw an error
   */
  suspend fun setActiveServerRecovery(activeServerRecovery: ServerRecovery?): Result<Unit, Error>

  /**
   * Clear server and local recovery db states.
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Update the dao with the latest progress of the local recovery. This will affect
   * the output of `getActiveRecovery()` by moving the recovery along to the next state.
   */
  suspend fun setLocalRecoveryProgress(progress: LocalRecoveryAttemptProgress): Result<Unit, Error>

  /**
   * Check if the localRecovery initiated, thus it presents.
   */
  suspend fun isLocalRecoveryPresent(): Result<Boolean, DbTransactionError>
}
