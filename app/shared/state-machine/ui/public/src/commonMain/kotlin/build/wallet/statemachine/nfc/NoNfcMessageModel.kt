@file:Suppress("FunctionName")

package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal fun NoNfcMessageModel(onBack: () -> Unit) =
  FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header =
      FormHeaderModel(
        headline = "NFC is not available on this device",
        subline =
          "Communication with Bitkey requires NFC." +
            "\n\nPlease try again with a device that supports this feature."
      ),
    primaryButton =
      ButtonModel(
        text = "Continue",
        onClick = StandardClick(onBack),
        size = Footer
      ),
    id = NfcEventTrackerScreenId.NFC_NOT_AVAILABLE
  )
