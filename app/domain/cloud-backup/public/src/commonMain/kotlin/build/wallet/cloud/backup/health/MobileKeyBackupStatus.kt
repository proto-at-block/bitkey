package build.wallet.cloud.backup.health

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.health.MobileKeyBackupStatus.Healthy
import kotlinx.datetime.Instant
import kotlin.contracts.contract

/**
 * Indicates the status of the mobile key backup.
 */
sealed interface MobileKeyBackupStatus {
  /**
   * Indicates that the backup is healthy, containing the last time the backup was uploaded.
   */
  data class Healthy(
    val lastUploaded: Instant,
  ) : MobileKeyBackupStatus

  /**
   * Indicates that there is a problem with the backup.
   */
  sealed interface ProblemWithBackup : MobileKeyBackupStatus {
    /**
     * Indicates that the backup is missing.
     */
    data object BackupMissing : ProblemWithBackup

    /**
     * Indicates that the app does not have access to the cloud storage.
     */
    data object NoCloudAccess : ProblemWithBackup

    /**
     * Indicates that the backup is found but is invalid:
     * - does not match the local backup
     * - does not belong to the customer's account
     */
    data class InvalidBackup(val cloudBackup: CloudBackup) : ProblemWithBackup
  }
}

fun MobileKeyBackupStatus.isHealthy(): Boolean {
  contract {
    returns(true) implies (this@isHealthy is Healthy)
  }
  return this is Healthy
}
