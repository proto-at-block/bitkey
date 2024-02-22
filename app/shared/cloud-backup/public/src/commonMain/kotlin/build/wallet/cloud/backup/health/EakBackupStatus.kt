@file:OptIn(ExperimentalContracts::class)

package build.wallet.cloud.backup.health

import build.wallet.cloud.backup.health.EakBackupStatus.Healthy
import kotlinx.datetime.Instant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Indicates the status of the Emergency Access Kit backup.
 */
sealed interface EakBackupStatus {
  /**
   * Indicates that the backup is healthy, containing the last time the backup was uploaded.
   */
  data class Healthy(
    val lastUploaded: Instant,
  ) : EakBackupStatus

  /**
   * Indicates that there is a problem with the backup.
   */
  sealed interface ProblemWithBackup : EakBackupStatus {
    /**
     * Indicates that the backup is missing.
     */
    data object BackupMissing : ProblemWithBackup

    /**
     * Indicates that the app does not have access to the cloud storage.
     */
    data object NoCloudAccess : ProblemWithBackup

    /**
     * Indicates that the backup is found but is invalid - it doesn't match the expected format,
     * or is corrupted.
     */
    data object InvalidBackup : ProblemWithBackup
  }
}

fun EakBackupStatus.isHealthy(): Boolean {
  contract {
    returns(true) implies (this@isHealthy is Healthy)
  }
  return this is Healthy
}
