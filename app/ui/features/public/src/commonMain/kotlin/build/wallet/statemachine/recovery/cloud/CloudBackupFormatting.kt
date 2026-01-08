package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.isFullAccount
import build.wallet.statemachine.core.Icon
import build.wallet.time.DateTimeFormatter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a CloudBackup into a CloudBackupItemModel for display in selection lists.
 *
 * Formatting rules:
 * - Display label: Uses device nickname if available (V3), otherwise "Account Type (id prefix...)"
 * - Secondary text: "Recovery Contact Backup" for lite accounts, date for full accounts (V3), account ID for V2
 * - Icons: SmallIconBitkey for full accounts, SmallIconShieldPerson for lite accounts
 *
 * @param backup The cloud backup to format
 * @param dateTimeFormatter Formatter to use for dates
 * @param timeZone Time zone to use for dates
 */
fun formatCloudBackupItemModel(
  backup: CloudBackup,
  dateTimeFormatter: DateTimeFormatter,
  timeZone: TimeZone,
): CloudBackupItemModel {
  val displayLabel = when (backup) {
    is CloudBackupV3 -> {
      // Use device nickname if available, otherwise fallback to account type
      backup.deviceNickname ?: formatFallbackLabel(backup)
    }
    is CloudBackupV2 -> formatFallbackLabel(backup)
  }

  val secondaryText = when {
    !backup.isFullAccount() -> "Recovery Contact Backup"
    backup is CloudBackupV3 -> {
      // Format: "Last backed up: 11/15/2025 at 6:30pm"
      val localDateTime = backup.createdAt
        .toLocalDateTime(timeZone)
      "Last backed up: ${dateTimeFormatter.fullShortDateWithTime(localDateTime)}"
    }
    else -> "Account ID: ${backup.accountId.take(12)}..."
  }

  val icon = if (backup.isFullAccount()) {
    Icon.SmallIconBitkey
  } else {
    Icon.SmallIconShieldPerson
  }

  return CloudBackupItemModel(
    backup = backup,
    displayLabel = displayLabel,
    secondaryText = secondaryText,
    icon = icon
  )
}

/**
 * Fallback label format when device nickname is not available.
 */
private fun formatFallbackLabel(backup: CloudBackup): String {
  val accountType = if (backup.isFullAccount()) "Wallet Backup" else "Recovery Contact Backup"
  val accountIdPrefix = backup.accountId.take(8)
  return "$accountType ($accountIdPrefix...)"
}
