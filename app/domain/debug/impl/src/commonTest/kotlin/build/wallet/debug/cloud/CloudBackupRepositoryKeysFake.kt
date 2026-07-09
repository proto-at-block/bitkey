package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupStoreKeys

/**
 * Fake key classifier for debug-cloud tests.
 *
 * Treats legacy `cloud-backup`, account-specific `cb-*`, older `backup-*`, and
 * archived `archive-*` keys as valid.
 */
internal class CloudBackupStoreKeysFake : CloudBackupStoreKeys {
  override fun isValidArchivedKey(key: String): Boolean = key.startsWith("archive-")

  override fun isValidBackupKey(key: String): Boolean =
    isLegacyActiveBackupKey(key) || key.startsWith("cb-") || key.startsWith("backup-")

  override fun archiveFormatKey(backup: CloudBackup): String = error("Not used in tests")

  override fun activeBackupFormatAccountSpecificKey(accountId: AccountId): String =
    "cb-${accountId.serverId}"

  override fun activeBackupFormatKey(backup: CloudBackup): String = error("Not used in tests")

  override fun isLegacyActiveBackupKey(key: String): Boolean = key == "cloud-backup"

  override fun isAccountSpecificActiveBackupKeyForAccount(
    key: String,
    accountId: AccountId,
  ): Boolean = key == activeBackupFormatAccountSpecificKey(accountId)
}
