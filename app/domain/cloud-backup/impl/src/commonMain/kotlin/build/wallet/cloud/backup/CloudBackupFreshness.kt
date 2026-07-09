package build.wallet.cloud.backup

internal fun CloudBackup.isFresherThan(other: CloudBackup): Boolean =
  when (this) {
    is CloudBackupV3 -> when (other) {
      is CloudBackupV3 -> createdAt > other.createdAt
      is CloudBackupV2 -> true
    }
    is CloudBackupV2 -> false
  }

internal fun List<CloudBackup>.freshestByAccount(): List<CloudBackup> =
  fold(LinkedHashMap<String, CloudBackup>()) { backupsByAccount, backup ->
    val existingBackup = backupsByAccount[backup.accountId]
    if (existingBackup == null || backup.isFresherThan(existingBackup)) {
      backupsByAccount[backup.accountId] = backup
    }
    backupsByAccount
  }.values.toList()

internal fun List<CloudBackup>.freshest(): CloudBackup? =
  reduceOrNull { freshestBackup, backup ->
    if (backup.isFresherThan(freshestBackup)) {
      backup
    } else {
      freshestBackup
    }
  }
