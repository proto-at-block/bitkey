package build.wallet.emergencyaccesskit

import build.wallet.cloud.backup.CloudBackupError

/**
 * Describes a failure that can occur when reading or writing the emergency access kit from cloud
 * file store.
 */
sealed class EmergencyAccessKitRepositoryError : Error() {
  data class UnrectifiableCloudError(
    override val cause: Throwable,
  ) : EmergencyAccessKitRepositoryError()

  data class RectifiableCloudError(
    override val cause: Throwable,
    /**
     * An Android Intent from a UserRecoverableAuthIOException, but passed as Any so this class can
     * exist in shared.
     */
    val data: Any,
  ) : EmergencyAccessKitRepositoryError() {
    val toRectifiableCloudBackupError: CloudBackupError.RectifiableCloudBackupError =
      CloudBackupError.RectifiableCloudBackupError(cause, data)
  }
}
