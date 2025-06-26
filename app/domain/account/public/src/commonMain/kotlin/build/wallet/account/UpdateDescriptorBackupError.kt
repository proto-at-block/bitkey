package build.wallet.account

/**
 * Errors that can occur when updating descriptor backups.
 */
sealed class UpdateDescriptorBackupError : Error() {
  /*
   * The backup payload is missing a backup for a specific keyset id.
   */
  data class MissingKeysetId(override val cause: Throwable) : UpdateDescriptorBackupError()

  /*
   * The backup payload contains a descriptor for a keyset that does not belong to the user.
   */
  data class KeysetIdNotFound(override val cause: Throwable) : UpdateDescriptorBackupError()

  /*
   * Request does not have a valid HW Proof of Possession.
   */
  data class HardwareFactorRequired(override val cause: Throwable) : UpdateDescriptorBackupError()

  data class Unspecified(override val cause: Throwable) : UpdateDescriptorBackupError()
}
