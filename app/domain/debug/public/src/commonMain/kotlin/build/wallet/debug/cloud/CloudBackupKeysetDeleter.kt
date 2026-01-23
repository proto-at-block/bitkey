package build.wallet.debug.cloud

import com.github.michaelbull.result.Result

/**
 * Used for debugging purposes through debug menu available
 * in Development and Team builds.
 *
 * Deletes the active keyset from the cloud backup, reverting to a previous keyset.
 * This simulates a backup that was created before the latest key rotation.
 * Useful for testing recovery flows that handle outdated backups.
 */
interface CloudBackupKeysetDeleter {
  /**
   * Deletes the active keyset from the cloud backup by replacing activeSpendingKeyset
   * with the last keyset from the keysets list (if available). This simulates
   * a backup that was created before the latest key rotation.
   * Assumes a cloud account is already signed in and multiple keys are available.
   */
  suspend fun deleteActiveKeyset(): Result<Unit, KeysetDeletionError>
}

sealed class KeysetDeletionError : Error() {
  abstract override val message: String

  data class CloudAccountError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KeysetDeletionError()

  data class BackupReadError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KeysetDeletionError()

  data class BackupWriteError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KeysetDeletionError()

  data class DecryptionError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KeysetDeletionError()

  data class PkekMissingError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KeysetDeletionError()
}
