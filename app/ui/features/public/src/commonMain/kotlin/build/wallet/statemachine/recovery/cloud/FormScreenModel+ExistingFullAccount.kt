package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.*
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class ExistingFullAccountFoundBodyModel(
  val devicePlatform: DevicePlatform,
  override val onBack: () -> Unit,
  val onRestore: () -> Unit,
  val onDeleteBackupAndCreateNew: () -> Unit,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(headline = "We found an existing wallet backup"),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            items = immutableListOf(
              ListItemModel(
                leadingAccessory =
                  ListItemAccessory.IconAccessory(
                    iconPadding = 12,
                    model = IconModel(icon = Icon.SmallIconRecovery, iconSize = IconSize.Small)
                  ),
                title = "Restore your wallet",
                secondaryText = when (devicePlatform) {
                  Android, Jvm -> "Access your wallet on this phone using the Google Drive backup of your mobile key."
                  IOS -> "Access your wallet on this phone using the iCloud backup of your mobile key."
                },
                onClick = onRestore,
                trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
              ),
              ListItemModel(
                leadingAccessory =
                  ListItemAccessory.IconAccessory(
                    iconPadding = 12,
                    model = IconModel(icon = Icon.SmallIconWarning, iconSize = IconSize.Small)
                  ),
                title = "Delete backup and create a new account",
                secondaryText = "If you previously had a Bitkey but are no longer using it, or you now want to become a Recovery Contact for someone else.",
                onClick = onDeleteBackupAndCreateNew,
                trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
              )
            ),
            style = ListGroupStyle.DIVIDER
          )
      )
    ),
    primaryButton = null
  )
