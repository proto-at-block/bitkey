package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

/**
 * Presentation model for displaying a cloud backup in a list.
 * Contains the original backup and pre-formatted display strings.
 *
 * This model separates formatting concerns from the domain model [CloudBackup],
 * allowing the UI layer to handle all date formatting and display logic.
 *
 * @property backup The underlying cloud backup data
 * @property displayLabel Primary display text (e.g., "Wallet Backup abc123")
 * @property secondaryText Secondary display text (e.g., "Last backed up: 11/15/2025")
 * @property icon Icon to display for this backup type
 */
data class CloudBackupItemModel(
  val backup: CloudBackup,
  val displayLabel: String,
  val secondaryText: String,
  val icon: Icon,
)

/**
 * Converts a [CloudBackupItemModel] to a [ListItemModel] for rendering in a list.
 *
 * @param onClick Callback invoked when the item is tapped, passing the underlying [CloudBackup]
 */
fun CloudBackupItemModel.toListItemModel(onClick: (CloudBackup) -> Unit) =
  ListItemModel(
    title = displayLabel,
    secondaryText = secondaryText,
    leadingAccessory = ListItemAccessory.IconAccessory(
      model = IconModel(
        icon = icon,
        iconSize = IconSize.Small
      )
    ),
    trailingAccessory = ListItemAccessory.drillIcon(),
    onClick = { onClick(backup) }
  )
