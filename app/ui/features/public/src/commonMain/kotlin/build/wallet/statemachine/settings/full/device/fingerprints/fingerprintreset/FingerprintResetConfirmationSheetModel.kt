package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class FingerprintResetConfirmationSheetModel(
  val onDismiss: () -> Unit,
  val onConfirmReset: () -> Unit,
) : FormBodyModel(
    id = FingerprintResetEventTrackerScreenId.TAP_DEVICE_TO_RESET_SHEET,
    onBack = onDismiss,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Wake your Bitkey device",
      subline = "Before continuing, press the fingerprint sensor until you see a light."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      onClick = StandardClick(onConfirmReset),
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet
  )
