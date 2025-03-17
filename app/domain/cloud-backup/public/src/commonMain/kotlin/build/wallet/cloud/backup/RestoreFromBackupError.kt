package build.wallet.cloud.backup

sealed class RestoreFromBackupError : Error() {
  /**
   * Error describing that the CSEK is missing from the [CsekDao].
   */
  data object CsekMissing : RestoreFromBackupError() {
    override val cause: Throwable? = null
    override val message: String = "Missing CSEK"
  }

  data class AccountBackupDecodingError(
    override val cause: Throwable? = null,
    override val message: String? = null,
  ) : RestoreFromBackupError()

  data class AccountBackupRestorationError(
    override val cause: Throwable? = null,
    override val message: String? = null,
  ) : RestoreFromBackupError()
}
