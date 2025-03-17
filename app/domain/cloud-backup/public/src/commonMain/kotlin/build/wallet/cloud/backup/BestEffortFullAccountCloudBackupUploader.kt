package build.wallet.cloud.backup

import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Result

/**
 * Uploads a [FullAccount] cloud backup
 *
 * The errors returned by this class assume Cloud Backup Health can take over for some types of
 * failures that can therefore be ignored, e.g. failures that prevent access to the cloud.
 */
fun interface BestEffortFullAccountCloudBackupUploader {
  /**
   * Upload a [FullAccount] cloud backup.
   *
   * Failures are divided into two categories:
   * [Failure.IgnorableError] and [Failure.BreakingError].
   */
  suspend fun createAndUploadCloudBackup(fullAccount: FullAccount): Result<Unit, Failure>

  sealed class Failure : Error() {
    /**
     * An error that can be ignored, i.e. one that would be noticed by Cloud Backup Health,
     * such as a cloud access error.
     */
    data class IgnorableError(
      override val message: String,
      override val cause: Throwable? = null,
    ) : Failure()

    /**
     * An error that cannot be ignored, i.e. one that would escape the notice of
     * Cloud Backup Health, such as failure to write to the cloud backup dao.
     */
    data class BreakingError(
      override val message: String,
      override val cause: Throwable? = null,
    ) : Failure()
  }
}
