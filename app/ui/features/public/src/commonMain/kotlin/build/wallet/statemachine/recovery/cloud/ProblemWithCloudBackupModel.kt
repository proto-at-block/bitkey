package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
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
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

sealed interface CloudBackupFailure {
  data object HWCantDecryptCSEK : CloudBackupFailure

  data object AppCantRestoreCloudBackup : CloudBackupFailure

  data object AppCantPerformPostRestorationSteps : CloudBackupFailure

  data object AppCantSetActiveKeybox : CloudBackupFailure

  data object CantFindCloudAccount : CloudBackupFailure
}

/**
 * Screen shown to customer when we found cloud backup but were not able to use it
 * (for example, due to schema serialization error, or expired auth keys).
 */
data class ProblemWithCloudBackupModel(
  override val onBack: () -> Unit,
  val onRecoverAppKey: () -> Unit,
  val failure: CloudBackupFailure,
) : FormBodyModel(
    id = FAILURE_RESTORE_FROM_CLOUD_BACKUP,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = when (failure) {
      CloudBackupFailure.HWCantDecryptCSEK -> FormHeaderModel(
        headline = "Problem with cloud backup",
        subline = "The Bitkey you tapped isn’t linked to the cloud backup we found. " +
          "Please double-check that you're using the Bitkey device associated with your wallet. " +
          "If you are, you can still recover your wallet by starting a 7-day security period which " +
          "will overwrite the existing backup in your cloud account."
      )
      CloudBackupFailure.CantFindCloudAccount,
      CloudBackupFailure.AppCantRestoreCloudBackup,
      CloudBackupFailure.AppCantPerformPostRestorationSteps,
      CloudBackupFailure.AppCantSetActiveKeybox,
      -> FormHeaderModel(
        headline = "Problem with cloud backup",
        subline = "There was an issue accessing your cloud backup."
      )
    },
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOfNotNull(
            ListItemModel(
              leadingAccessory = IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = Icon.SmallIconWallet,
                  iconSize = IconSize.Small
                )
              ),
              title = "Recover your wallet",
              onClick = onRecoverAppKey,
              trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
            )
          ),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = null
  )
