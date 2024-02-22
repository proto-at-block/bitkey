package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Syncs the account's active Recovery and caches it on the client, providing information about
 * how the server's active recovery compares to any local attempt the client has.
 */
interface RecoverySyncer {
  /**
   * Unary sync request. Gets the server's active recovery, caches it, and returns a [Recovery]
   * which indicates, if there is an active server recovery, whether it matches any active recovery
   * that the client is attempting.
   */
  suspend fun performSync(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, SyncError>

  /**
   * Initiates a polling queue from which `performSync` is called at a regular interval.
   */
  fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  )

  /**
   * A flow that emits whenever the recovery status changes. This could be an advancement
   * of a local recovery to a new phase, or entering a state where our local recovery attempt.
   */
  fun recoveryStatus(): Flow<Result<Recovery, DbError>>

  /**
   * Clears server and local recovery db states.
   */
  suspend fun clear(): Result<Unit, DbError>

  /**
   * Moves a local recovery along the completion path by specifying the relevant progress made on it.
   */
  suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, DbError>

  /**
   * Represents an error with the Syncing process
   */
  sealed class SyncError : Error() {
    /**
     * Error if we can't read/write from the Database.
     */
    data class SyncDbError(val error: DbError) : SyncError()

    /**
     * Error if we cannot fetch the recovery from the server
     */
    data class CouldNotFetchServerRecovery(val error: NetworkingError) : SyncError()
  }
}
