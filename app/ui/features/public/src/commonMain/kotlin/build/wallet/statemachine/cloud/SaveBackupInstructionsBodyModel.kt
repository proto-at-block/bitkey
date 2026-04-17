package build.wallet.statemachine.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.*
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class SaveBackupInstructionsBodyModel(
  val requiresHardware: Boolean,
  val isLoading: Boolean,
  val onBackupClick: () -> Unit,
  val onLearnMoreClick: () -> Unit,
  val devicePlatform: DevicePlatform,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS,
    onBack = null,
    toolbar = ToolbarModel(
      trailingAccessory = ToolbarAccessoryModel.ButtonAccessory(
        model = ButtonModel(
          text = "Learn more",
          treatment = ButtonModel.Treatment.TertiaryPrimary,
          onClick = StandardClick(onLearnMoreClick),
          size = ButtonModel.Size.Compact
        )
      )
    ),
    header = FormHeaderModel(
      headline = backupInstructionsTitle(devicePlatform),
      subline = backupInstructionsSubline(devicePlatform)
    ),
    mainContentList = saveBackupInstructionsMainContentList(
      devicePlatform = devicePlatform,
      appKeyIcon = Icon.SmallIconMobileKey,
      emergencyExitKitIcon = Icon.SmallIconRecovery,
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      title = backupInstructionsTitle(devicePlatform),
      header = FormHeaderModel(
        headline = null,
        sublineModel = StringModel(backupInstructionsSubline(devicePlatform))
      ),
      mainContentList = saveBackupInstructionsMainContentList(
        devicePlatform = devicePlatform,
        appKeyIcon = Icon.DotCloudBackup,
        emergencyExitKitIcon = Icon.DotEmergency,
        leadingIconSize = IconSize.Regular,
      ),
      scrollable = false,
      mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.BOTTOM
    ),
    primaryButton = ButtonModel(
      text = "Back up",
      requiresBitkeyInteraction = requiresHardware,
      onClick = onBackupClick,
      isLoading = isLoading,
      size = ButtonModel.Size.Footer,
      treatment = Primary
    )
  )

private fun saveBackupInstructionsMainContentList(
  devicePlatform: DevicePlatform,
  appKeyIcon: Icon,
  emergencyExitKitIcon: Icon,
  leadingIconSize: IconSize = IconSize.Small,
) = immutableListOf(
  FormMainContentModel.Explainer(
    items = immutableListOf(
      FormMainContentModel.Explainer.Statement(
        leadingIcon = appKeyIcon,
        leadingIconSize = leadingIconSize,
        title = "App Key",
        body = "If you ever get a new phone, simply restore your wallet with this backup and your Bitkey device."
      ),
      FormMainContentModel.Explainer.Statement(
        leadingIcon = emergencyExitKitIcon,
        leadingIconSize = leadingIconSize,
        title = "Emergency Exit Kit",
        body = when (devicePlatform) {
          Android, Jvm -> "If the Bitkey app is unavailable, you’ll be able to use this Emergency Exit Kit document located in your Google Drive to maintain self-custody."
          IOS -> "If the Bitkey app is unavailable, you’ll be able to use this Emergency Exit Kit document located in your iCloud Drive to maintain self-custody."
        }
      )
    )
  )
)

private fun backupInstructionsTitle(devicePlatform: DevicePlatform): String =
  when (devicePlatform) {
    Android, Jvm -> "Back up to Google Drive"
    IOS -> "Back up to iCloud"
  }

private fun backupInstructionsSubline(devicePlatform: DevicePlatform): String =
  when (devicePlatform) {
    Android, Jvm -> "Sensitive data stored in your Google Drive is encrypted and only accessible with your Bitkey device."
    IOS -> "Sensitive data stored in your iCloud is encrypted and only accessible with your Bitkey device."
  }
