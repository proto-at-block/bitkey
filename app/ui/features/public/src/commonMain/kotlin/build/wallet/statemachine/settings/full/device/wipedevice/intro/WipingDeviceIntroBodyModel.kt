package build.wallet.statemachine.settings.full.device.wipedevice.intro

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class WipingDeviceIntroBodyModel(
  val presentedModally: Boolean,
  override val onBack: () -> Unit,
  val onWipeDevice: () -> Unit,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_INTRO,
    onBack = null,
    toolbar = ToolbarModel(
      leadingAccessory = if (presentedModally) {
        ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
      } else {
        ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
      }
    ),
    header = FormHeaderModel(
      headline = "Permanently wipe your device",
      subline = "Always transfer the funds from this wallet to a new wallet before wiping this device.\n\n" +
        "Wiping a device can lead to permanent loss of funds."
    ),
    primaryButton = ButtonModel(
      text = "Wipe device",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick { onWipeDevice() }
    )
  )
