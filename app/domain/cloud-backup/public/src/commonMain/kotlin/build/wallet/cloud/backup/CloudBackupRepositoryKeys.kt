package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId

interface CloudBackupRepositoryKeys {
  /**
   * Find archived backup keys - both account-specific format (cb-*-timestamp) and legacy format (cloud-backup-timestamp)
   * Archived keys have a timestamp suffix after the base key
   * Format: cb-<account-id>-<Instant> or cloud-backup-<Instant>
   * AccountId produces format like "urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK"
   * Instant.toString() produces format like "2024-01-15T12:34:56.789Z"
   */
  fun isValidArchivedKey(key: String): Boolean

  /**
   * Find already written backup keys - both account-specific format (cb-*) and legacy format (cloud-backup)
   * Format: cb-<account-id> or cloud-backup
   * AccountId produces format like "urn:wallet-account:01KBFS9P38V24K2FHTCK1EXWPK"
   */
  fun isValidBackupKey(key: String): Boolean

  fun archiveFormatKey(backup: CloudBackup): String

  fun activeBackupFormatAccountSpecificKey(accountId: AccountId): String

  fun activeBackupFormatKey(backup: CloudBackup): String
}
