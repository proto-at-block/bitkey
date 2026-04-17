package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupStoreKeys

/**
 * Fake key classifier for debug-cloud tests.
 *
 * Treats keys prefixed with `backup-` and `archive-` as valid and keeps unsupported
 * formatting APIs unimplemented because tests only need key classification behavior.
 */
internal class CloudBackupStoreKeysFake : CloudBackupStoreKeys {
  override fun isValidArchivedKey(key: String): Boolean = key.startsWith("archive-")

  override fun isValidBackupKey(key: String): Boolean = key.startsWith("backup-")

  override fun archiveFormatKey(backup: CloudBackup): String = error("Not used in tests")

  override fun activeBackupFormatAccountSpecificKey(accountId: AccountId): String =
    error("Not used in tests")

  override fun activeBackupFormatKey(backup: CloudBackup): String = error("Not used in tests")

  override fun isLegacyActiveBackupKey(key: String): Boolean = false

  override fun isAccountSpecificActiveBackupKeyForAccount(
    key: String,
    accountId: AccountId,
  ): Boolean = false
}
