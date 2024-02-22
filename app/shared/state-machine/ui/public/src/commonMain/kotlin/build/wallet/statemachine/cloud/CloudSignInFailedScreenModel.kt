package build.wallet.statemachine.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.device.DevicePlatform.Jvm
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun CloudSignInFailedScreenModel(
  onTryAgain: () -> Unit,
  onBack: () -> Unit,
  devicePlatform: DevicePlatform,
) = FormBodyModel(
  id = CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_NOT_SIGNED_IN,
  onBack = onBack,
  toolbar =
    ToolbarModel(leadingAccessory = BackAccessory(onBack)),
  header =
    FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline =
        when (devicePlatform) {
          Android, Jvm -> "You’re not signed in to Google"
          IOS -> "You’re not signed in to iCloud"
        },
      subline =
        when (devicePlatform) {
          Android, Jvm ->
            "Sign in to Google in order to save a copy of the key from your phone to your personal cloud, so you can easily recover your wallet on a new phone."
          IOS ->
            "Open your iPhone settings, sign into iCloud, and try again."
        }
    ),
  primaryButton =
    ButtonModel(
      text =
        when (devicePlatform) {
          Android, Jvm -> "Sign in to Google"
          IOS -> "Check again"
        },
      onClick = Click.standardClick { onTryAgain() },
      size = ButtonModel.Size.Footer
    )
)
