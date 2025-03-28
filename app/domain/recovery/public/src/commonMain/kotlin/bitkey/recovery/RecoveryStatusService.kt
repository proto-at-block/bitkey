package bitkey.recovery

import build.wallet.ktor.result.NetworkingError
import build.wallet.recovery.LocalRecoveryAttemptProgress
import build.wallet.recovery.Recovery
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Syncs the account's active Recovery and caches it on the client, providing information about
 * how the server's active recovery compares to any local attempt the client has.
 */
interface RecoveryStatusService {
  /**
   * A flow that emits whenever the recovery status changes. This could be an advancement
   * of a local recovery to a new phase, or entering a state where our local recovery attempt.
   */
  fun status(): Flow<Result<Recovery, Error>>

  /**
   * Clears server and local recovery db states.
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Moves a local recovery along the completion path by specifying the relevant progress made on it.
   */
  suspend fun setLocalRecoveryProgress(progress: LocalRecoveryAttemptProgress): Result<Unit, Error>

  /**
   * Represents an error with the Syncing process
   */
  sealed class SyncError : Error() {
    /**
     * Error if we can't read/write from the Database.
     */
    data class SyncDbError(val error: Error) : SyncError()

    /**
     * Error if we cannot fetch the recovery from the server
     */
    data class CouldNotFetchServerRecovery(val error: NetworkingError) : SyncError()
  }
}
