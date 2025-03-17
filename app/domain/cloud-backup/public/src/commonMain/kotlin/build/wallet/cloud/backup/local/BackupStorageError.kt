package build.wallet.cloud.backup.local

/** Describes an error that can occur when reading/writing a backup in local storage. */
data class BackupStorageError(
  override val cause: Throwable? = null,
  override val message: String? = null,
) : Error()
