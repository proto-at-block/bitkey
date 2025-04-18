package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

data class AddAdditionalFingerprintGettingStartedModel(
  val onClosed: () -> Unit,
  val onContinue: () -> Unit,
  val onSetUpLater: () -> Unit,
) : FormBodyModel(
    id = ManagingFingerprintsEventTrackerScreenId.ADD_ADDITIONAL_FINGERPRINT_EXPLAINER,
    onBack = onClosed,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.SmallIconFingerprint,
        iconSize = IconSize.Large,
        iconTint = IconTint.Primary,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Avatar,
          color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
        ),
        iconTopSpacing = 0
      ),
      headline = "Add additional fingerprint",
      alignment = FormHeaderModel.Alignment.LEADING,
      subline = "Enable additional fingers to unlock your Bitkey hardware."
    ),
    primaryButton =
      ButtonModel(
        text = "Add additional fingerprint",
        leadingIcon = Icon.SmallIconBitkey,
        onClick = SheetClosingClick(onContinue),
        treatment = ButtonModel.Treatment.BitkeyInteraction,
        size = ButtonModel.Size.Footer
      ),
    secondaryButton = ButtonModel(
      text = "Set up later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onSetUpLater)
    ),
    renderContext = RenderContext.Sheet
  )
