package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Screen shown when fingerprint enrollment fails or wasn't saved successfully.
 */
data class FingerprintResetEnrollmentFailureBodyModel(
  val onBackClick: () -> Unit,
  val onTryAgain: () -> Unit,
) : FormBodyModel(
    id = FingerprintResetEventTrackerScreenId.FINGERPRINT_RESET_ENROLLMENT_TRY_AGAIN,
    onBack = onBackClick,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory(onClick = onBackClick)
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Let's try this again",
      subline = "Your fingerprint wasn't saved, but your device is ready to try again."
    ),
    primaryButton = ButtonModel(
      text = "Try again",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onTryAgain)
    )
  )
