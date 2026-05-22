package build.wallet.statemachine.settings.full.device.wipedevice

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel

data class ScanDeviceToWipeSheetBodyModel(
  override val onBack: () -> Unit,
  val onScanToContinue: () -> Unit,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_SCAN_SHEET,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Permanently wipe your device",
      subline = "Start by scanning the device you want to wipe."
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Scan to continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onScanToContinue() },
      treatment = ButtonModel.Treatment.Primary
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBack)
    ),
    renderContext = RenderContext.Sheet
  )
