package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.callout.CalloutModel

data class ScanAndWipeConfirmationSheetBodyModel(
  override val onBack: () -> Unit,
  val onConfirmWipeDevice: () -> Unit,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.SCAN_AND_RESET_SHEET,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Scan to wipe device",
      subline = "Hold your unlocked device to the back of your phone to wipe."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Callout(
        item = CalloutModel(
          title = "This cannot be undone",
          subtitle = StringModel("This will permanently wipe your Bitkey device."),
          treatment = CalloutModel.Treatment.Danger
        )
      )
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Scan and wipe",
      treatment = ButtonModel.Treatment.PrimaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onConfirmWipeDevice() }
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onBack)
    ),
    renderContext = RenderContext.Sheet
  )
