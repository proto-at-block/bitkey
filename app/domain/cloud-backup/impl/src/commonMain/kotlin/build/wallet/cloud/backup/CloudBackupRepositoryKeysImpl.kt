package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.feature.isEnabled
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class CloudBackupRepositoryKeysImpl(
  private val sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
  private val clock: Clock,
) : CloudBackupRepositoryKeys {
  // Key used to store backups in cloud key-value store
  private val cloudBackupLegacyKeyPrefix = "cloud-backup"

  // Key prefix for cloud backup account-specific
  private val cloudBackupAccountSpecificKeyPrefix = "cb-"

  override fun isValidArchivedKey(key: String): Boolean {
    // This regex finds an ISO 8601 timestamp (like one from Instant.toString())
    // at the very end of the key string.
    val timestampRegex = getTimestampRegex()
    // The key ends with a string that looks like a timestamp.
    val match = timestampRegex.find(key) ?: return false

    // If a timestamp was found, check the part of the key that comes before it.
    val timestamp = match.value
    val prefix = key.removeSuffix(timestamp)

    // Check for legacy format: "cloud-backup-<Instant>"
    if (prefix == cloudBackupLegacyKeyPrefix) {
      return true
    }

    // Check for account-specific format: "cb-<account-id>-<Instant>"
    // This ensures the prefix starts with "cb-", ends with "-",
    // and has an account ID in between.
    if (prefix.startsWith(cloudBackupAccountSpecificKeyPrefix) && prefix.length > cloudBackupAccountSpecificKeyPrefix.length) {
      return true
    }

    return false
  }

  override fun isValidBackupKey(key: String): Boolean {
    // a legacy key
    if (key == cloudBackupLegacyKeyPrefix) return true

    // This regex finds an ISO 8601 timestamp (like one from Instant.toString())
    // at the very end of the key string.
    val timestampRegex = getTimestampRegex()

    // an account-specific key
    return timestampRegex.find(key) == null &&
      key.startsWith(cloudBackupAccountSpecificKeyPrefix) &&
      !key.endsWith("-")
  }

  private fun getTimestampRegex(): Regex =
    Regex("""(-\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z)$""")

  override fun archiveFormatKey(backup: CloudBackup): String =
    if (sharedCloudBackupsFeatureFlag.isEnabled()) {
      "$cloudBackupAccountSpecificKeyPrefix${backup.accountId}-${clock.now()}"
    } else {
      "$cloudBackupLegacyKeyPrefix-${clock.now()}"
    }

  override fun activeBackupFormatAccountSpecificKey(accountId: AccountId): String =
    "$cloudBackupAccountSpecificKeyPrefix${accountId.serverId}"

  override fun activeBackupFormatKey(backup: CloudBackup): String =
    if (sharedCloudBackupsFeatureFlag.isEnabled()) {
      "$cloudBackupAccountSpecificKeyPrefix${backup.accountId}"
    } else {
      cloudBackupLegacyKeyPrefix
    }
}
