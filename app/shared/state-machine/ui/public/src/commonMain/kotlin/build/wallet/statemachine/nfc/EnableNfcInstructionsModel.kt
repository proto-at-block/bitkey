@file:Suppress("FunctionName")

package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal fun EnableNfcInstructionsModel(
  onBack: () -> Unit,
  onEnableClick: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
  header =
    FormHeaderModel(
      headline = "Please enable NFC",
      subline =
        "Communication with Bitkey requires NFC." +
          "\n\nPlease click the button below to navigate to Settings and activate NFC."
    ),
  primaryButton =
    ButtonModel(
      text = "Enable",
      onClick = Click.standardClick { onEnableClick() },
      size = Footer
    ),
  id = NfcEventTrackerScreenId.NFC_ENABLE_INSTRUCTIONS
)
