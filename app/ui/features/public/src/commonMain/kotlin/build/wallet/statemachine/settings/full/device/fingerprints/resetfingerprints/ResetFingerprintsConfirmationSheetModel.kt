package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class ResetFingerprintsConfirmationSheetModel(
  val onDismiss: () -> Unit,
  val onConfirmReset: () -> Unit,
) : FormBodyModel(
    id = ResetFingerprintsEventTrackerScreenId.TAP_DEVICE_TO_RESET_SHEET,
    onBack = onDismiss,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Wake your Bitkey device",
      subline = "Press the fingerprint sensor until you see a red light to wake your Bitkey."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Bitkey is awake",
      onClick = StandardClick(onConfirmReset),
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet
  )
