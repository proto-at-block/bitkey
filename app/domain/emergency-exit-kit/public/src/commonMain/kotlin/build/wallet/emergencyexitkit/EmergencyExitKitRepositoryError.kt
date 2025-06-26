package build.wallet.emergencyexitkit

import build.wallet.cloud.backup.CloudBackupError

/**
 * Describes a failure that can occur when reading or writing the Emergency Exit Kit from cloud
 * file store.
 */
sealed class EmergencyExitKitRepositoryError : Error() {
  data class UnrectifiableCloudError(
    override val cause: Throwable,
  ) : EmergencyExitKitRepositoryError()

  data class RectifiableCloudError(
    override val cause: Throwable,
    /**
     * An Android Intent from a UserRecoverableAuthIOException, but passed as Any so this class can
     * exist in shared.
     */
    val data: Any,
  ) : EmergencyExitKitRepositoryError() {
    val toRectifiableCloudBackupError: CloudBackupError.RectifiableCloudBackupError =
      CloudBackupError.RectifiableCloudBackupError(cause, data)
  }
}
