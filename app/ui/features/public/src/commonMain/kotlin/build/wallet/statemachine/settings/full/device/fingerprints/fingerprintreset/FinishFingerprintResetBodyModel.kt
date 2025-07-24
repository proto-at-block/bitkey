package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class FinishFingerprintResetBodyModel(
  val onClose: () -> Unit,
  val onConfirmReset: () -> Unit,
  val onCancelReset: () -> Unit,
) : FormBodyModel(
    id = FingerprintResetEventTrackerScreenId.CONFIRM_RESET_FINGERPRINTS,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClose),
      trailingAccessory = ButtonAccessory(
        model = ButtonModel(
          text = "Cancel reset",
          treatment = TertiaryDestructive,
          size = Compact,
          onClick = StandardClick { onCancelReset() }
        )
      )
    ),
    header = FormHeaderModel(
      headline = "Finish fingerprint reset",
      subline = "The security period for resetting your fingerprints is complete. You can now reset and add new fingerprints to your Bitkey device."
    ),
    primaryButton = ButtonModel(
      text = "Reset fingerprints",
      onClick = StandardClick(onConfirmReset),
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    )
  )
