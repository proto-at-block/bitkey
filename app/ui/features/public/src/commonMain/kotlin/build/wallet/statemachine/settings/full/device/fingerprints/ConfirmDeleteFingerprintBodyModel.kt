package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class ConfirmDeleteFingerprintBodyModel(
  val onDelete: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = ManagingFingerprintsEventTrackerScreenId.CONFIRM_DELETE_FINGERPRINT,
    onBack = onCancel,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(
        onCancel
      )
    ),
    header = FormHeaderModel(
      headline = "Are you sure you want to delete your fingerprint?",
      subline = "You can’t undo this action. You must have at least one fingerprint saved to device."
    ),
    // TODO (W-7901): Show a read-only version of the fingerprint label here.
    primaryButton = BitkeyInteractionButtonModel(
      text = "Delete fingerprint",
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onDelete)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
    renderContext = RenderContext.Sheet
  )
