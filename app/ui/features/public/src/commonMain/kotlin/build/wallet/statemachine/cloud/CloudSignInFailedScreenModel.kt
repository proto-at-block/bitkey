package build.wallet.statemachine.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.*
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.designSystemV2HeroIconHeader
import build.wallet.statemachine.recovery.cloud.iCloudTroubleshootingStepsMainContentList
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.market.MarketIcons

data class CloudSignInFailedScreenModel(
  val onContactSupport: () -> Unit,
  val onTryAgain: () -> Unit,
  override val onBack: () -> Unit,
  val devicePlatform: DevicePlatform,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_NOT_SIGNED_IN,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onBack).takeIf {
        devicePlatform == IOS
      } ?: BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled.takeUnless { devicePlatform == IOS },
      headline = when (devicePlatform) {
        Android, Jvm -> "You’re not signed in to Google"
        IOS -> "Check your iCloud settings"
      },
      subline = when (devicePlatform) {
        Android, Jvm ->
          "Sign in to Google in order to save a copy of the key from your phone to your " +
            "personal cloud, so you can easily recover your wallet on a new phone."
        IOS -> null
      }
    ),
    mainContentList = when (devicePlatform) {
        Android, Jvm -> immutableListOf()
      IOS -> iCloudTroubleshootingStepsMainContentList()
    },
    designSystemV2Model = FormDesignSystemV2Model(
      header = designSystemV2HeroIconHeader(
        headline = when (devicePlatform) {
          Android, Jvm -> "You’re not signed in to Google"
          IOS -> "Check your iCloud settings"
        },
        subline = when (devicePlatform) {
          Android, Jvm ->
            "Sign in to Google in order to save a copy of the key from your phone to your " +
              "personal cloud, so you can easily recover your wallet on a new phone."
          IOS -> null
        },
        icon = when (devicePlatform) {
          Android, Jvm -> MarketIcons.Cloud1Slash
          IOS -> MarketIcons.Cloud1Slash
        },
        iconTint = IconTint.White
      )
    ),
    primaryButton = RetryCloudSignInButton(
      androidText = "Sign in to Google",
      onTryAgain = onTryAgain,
      devicePlatform = devicePlatform
    ),
    secondaryButton = ButtonModel(
      leadingIcon = Icon.SmallIconArrowUpRight,
      text = "Customer support",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick =
        StandardClick { onContactSupport() }
    ).takeIf { devicePlatform == IOS }
  )

fun RetryCloudSignInButton(
  androidText: String,
  iOSText: String = "Check again",
  onTryAgain: () -> Unit,
  devicePlatform: DevicePlatform,
  treatment: ButtonModel.Treatment = ButtonModel.Treatment.Primary,
): ButtonModel =
  ButtonModel(
    leadingIcon = Icon.SmallIconRefresh.takeIf { devicePlatform == IOS },
    treatment = treatment,
    text = when (devicePlatform) {
      Android, Jvm -> androidText
      IOS -> iOSText
    },
    onClick = StandardClick(onTryAgain),
    size = ButtonModel.Size.Footer
  )
