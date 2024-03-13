package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.device.DevicePlatform.Jvm
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

private const val ANDROID_SUBLINE = "This Cloud already contains a backup for a different Bitkey account. Bitkey is designed to use only a single cloud account per Bitkey wallet, with a single backup, and creating a backup for this new account will erase the one currently in place. To recover from the existing backup, cancel setup and use \"Restore your wallet\". Otherwise, please either be certain you know the Bitkey backup currently in the connected Google Drive account is no longer needed before proceeding, or sign into a different cloud account."
private const val IOS_SUBLINE = "This Cloud already contains a backup for a different Bitkey account. Bitkey is designed to use only a single cloud account per Bitkey wallet, with a single backup, and creating a backup for this new account will erase the one currently in place. To recover from the existing backup, cancel setup and use \"Restore your wallet\". Otherwise, please either be certain you know the Bitkey backup currently in the connected iCloud account is no longer needed before proceeding, or sign into a different cloud account."

@Suppress("FunctionName")
fun OverwriteFullAccountCloudBackupWarningModel(
  onOverwriteExistingBackup: () -> Unit,
  onCancel: () -> Unit,
  devicePlatform: DevicePlatform,
) = FormBodyModel(
  header =
    FormHeaderModel(
      headline = "Old Bitkey backup found",
      subline =
        when (devicePlatform) {
          Jvm,
          Android,
          -> ANDROID_SUBLINE
          IOS -> IOS_SUBLINE
        }
    ),
  primaryButton =
    ButtonModel(
      text = "Overwrite the existing backup",
      onClick = StandardClick(onOverwriteExistingBackup),
      size = ButtonModel.Size.Footer
    ),
  secondaryButton =
    ButtonModel(
      text = "Cancel",
      onClick = StandardClick(onCancel),
      size = ButtonModel.Size.Footer
    ),
  onBack = null,
  toolbar = null,
  id = CloudEventTrackerScreenId.OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING
)
