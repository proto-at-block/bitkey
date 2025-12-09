package build.wallet.cloud.backup

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment

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
    is CloudBackupV3 -> fullAccountFields != null
  }
}

/**
 * Determines if a Cloud backup is using the latest backup schema version.
 * Returns `false` for older versions (V2) that should be migrated to V3.
 * Returns `true` for the current version (V3).
 *
 * When adding a new version (V4), update this to return `false` for V3 and `true` for V4.
 */
val CloudBackup.isLatestVersion: Boolean
  get() = when (this) {
    is CloudBackupV2 -> false // V2 should be migrated to V3
    is CloudBackupV3 -> true // V3 is the latest version
  }

/**
 * Extension property to access [BitcoinNetworkType] from both CloudBackupV2 and CloudBackupV3.
 */
val CloudBackup.bitcoinNetworkType: BitcoinNetworkType
  get() = when (this) {
    is CloudBackupV2 -> bitcoinNetworkType
    is CloudBackupV3 -> bitcoinNetworkType
  }

/**
 * Extension property to access [F8eEnvironment] from both CloudBackupV2 and CloudBackupV3.
 */
val CloudBackup.f8eEnvironment: F8eEnvironment
  get() = when (this) {
    is CloudBackupV2 -> f8eEnvironment
    is CloudBackupV3 -> f8eEnvironment
  }

/**
 * Extension property to access [isTestAccount] from both CloudBackupV2 and CloudBackupV3.
 */
val CloudBackup.isTestAccount: Boolean
  get() = when (this) {
    is CloudBackupV2 -> isTestAccount
    is CloudBackupV3 -> isTestAccount
  }
