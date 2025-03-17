package build.wallet.cloud.backup

/**
 * Indicates an error during the process of backup restoration.
 */
sealed class FullAccountRestorationError : Error() {
  data class BackupFormatErrorFull(
    override val cause: Throwable? = null,
    override val message: String,
  ) : FullAccountRestorationError()

  data class DecodingErrorFull(
    override val cause: Throwable? = null,
    override val message: String,
  ) : FullAccountRestorationError()

  data class FailedToStorePrivateKeys(
    override val cause: Throwable,
    override val message: String,
  ) : FullAccountRestorationError()
}
