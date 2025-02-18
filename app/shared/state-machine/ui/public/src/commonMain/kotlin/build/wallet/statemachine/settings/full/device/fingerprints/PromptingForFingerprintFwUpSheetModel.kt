package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

fun PromptingForFingerprintFwUpSheetModel(
  onCancel: () -> Unit,
  onUpdate: () -> Unit,
) = PromptingForFingerprintFwUpSheetBodyModel(
  onCancel = onCancel,
  onUpdate = onUpdate
).asSheetModalScreen(onClosed = onCancel)

private data class PromptingForFingerprintFwUpSheetBodyModel(
  val onCancel: () -> Unit,
  val onUpdate: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onCancel,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.SmallIconWarning,
        iconSize = IconSize.Large,
        iconTint = IconTint.Primary,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Avatar,
          color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
        ),
        iconTopSpacing = 0
      ),
      headline = "Update your hardware device",
      subline = "Looks like you need to update your hardware device to add additional fingerprints."
    ),
    primaryButton = ButtonModel(
      text = "I'll do this later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onCancel)
    ),
    secondaryButton = BitkeyInteractionButtonModel(
      text = "Update hardware",
      onClick = SheetClosingClick(onUpdate),
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet
  )
