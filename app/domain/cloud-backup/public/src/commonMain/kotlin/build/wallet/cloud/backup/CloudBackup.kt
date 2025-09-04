package build.wallet.cloud.backup

/**
 * Represents a backup of all account data including private material.
 *
 * In case of full accounts, this instance will store hardware-encrypted keybox data
 * (public and private keys).
 *
 * In case of lite accounts, this will store unencrypted data (account id, authentication
 * keys).
 *
 * The instance of [CloudBackup] is what ends up being stored to customer's remote cloud
 * storage (Google Drive or iCloud Keychain).
 *
 * Serialization is handled internally by [CloudBackupRepository].
 *
 * Cloud backups use an immutable versioning pattern where each schema change requires
 * a new data class (e.g., [CloudBackupV2], CloudBackupV3).
 */
sealed interface CloudBackup {
  val accountId: String
}

/**
 * Determines if a Cloud backup is for a full account.
 */
fun CloudBackup.isFullAccount(): Boolean {
  return when (this) {
    is CloudBackupV2 -> fullAccountFields != null
  }
}
