package build.wallet.cloud.backup

/**
 * Describes a failure that can occur when reading or writing backup from cloud storage.
 */
sealed class CloudBackupError : Error() {
  data class UnrectifiableCloudBackupError(
    override val cause: Throwable,
  ) : CloudBackupError()

  data class RectifiableCloudBackupError(
    override val cause: Throwable,
    /**
     * An Android Intent from a UserRecoverableAuthIOException, but passed as Any so this class can
     * exist in shared.
     */
    val data: Any,
  ) : CloudBackupError()
}
