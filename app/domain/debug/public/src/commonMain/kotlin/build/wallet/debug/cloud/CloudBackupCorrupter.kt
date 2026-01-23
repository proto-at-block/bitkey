package build.wallet.debug.cloud

import com.github.michaelbull.result.Result

/**
 * Used for debugging purposes through debug menu available
 * in Development and Team builds.
 *
 * Corrupts cloud backup to simulate backup corruption scenarios
 * for testing recovery flows.
 *
 * Cloud backups should NEVER be corrupted in production code.
 */
interface CloudBackupCorrupter {
  /**
   * Corrupts cloud backup for the current cloud provider by writing
   * invalid data. Assumes a cloud account is already signed in.
   */
  suspend fun corrupt(): Result<Unit, CorruptionError>
}

sealed class CorruptionError : Error() {
  abstract override val message: String

  data class CustomerBuild(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CorruptionError()

  data class CloudAccountError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CorruptionError()

  data class BackupReadError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CorruptionError()

  data class DeserializationError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CorruptionError()

  data class BackupWriteError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : CorruptionError()
}
