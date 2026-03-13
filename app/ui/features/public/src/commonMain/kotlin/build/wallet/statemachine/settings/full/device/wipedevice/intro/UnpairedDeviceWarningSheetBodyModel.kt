package build.wallet.statemachine.settings.full.device.wipedevice.intro

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class UnpairedDeviceWarningSheetBodyModel(
  val subline: String,
  val onWipeDevice: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_UNPAIRED_WARNING,
    onBack = onCancel,
    toolbar = null,
    header = FormHeaderModel(
      headline = "This Bitkey device isn\u2019t paired to this app",
      subline = subline
    ),
    primaryButton = ButtonModel(
      text = "Wipe device",
      requiresBitkeyInteraction = false,
      onClick = onWipeDevice,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.PrimaryDanger
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
    renderContext = RenderContext.Sheet
  )
