package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * The success screen shown after a fingerprint has been successfully enrolled via the
 * fingerprint reset flow.
 */
data class FingerprintResetSuccessBodyModel(
  val onDone: () -> Unit,
) : FormBodyModel(
    id = FingerprintResetEventTrackerScreenId.FINGERPRINT_RESET_SUCCESS,
    onBack = onDone,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Fingerprint successfully saved",
      subline = "You can now use your saved fingerprint to unlock your Bitkey device."
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      onClick = StandardClick(onDone),
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    )
  )
