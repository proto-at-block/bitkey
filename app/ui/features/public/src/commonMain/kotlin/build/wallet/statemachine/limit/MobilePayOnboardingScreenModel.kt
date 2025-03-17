package build.wallet.statemachine.limit

import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

data class MobilePayOnboardingScreenModel(
  val onContinue: () -> Unit,
  val onSetUpLater: () -> Unit,
  val onClosed: () -> Unit,
  val headerHeadline: String,
  val headerSubline: String,
  val primaryButtonString: String,
) : FormBodyModel(
    id = MobilePayEventTrackerScreenId.ENABLE_MOBILE_PAY_INSTRUCTIONS,
    onBack = onClosed,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.SmallIconPhone,
        iconSize = IconSize.Large,
        iconTint = IconTint.Primary,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Avatar,
          color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
        ),
        iconTopSpacing = 0
      ),
      headline = headerHeadline,
      alignment = FormHeaderModel.Alignment.LEADING,
      subline = headerSubline
    ),
    primaryButton = ButtonModel(
      text = primaryButtonString,
      size = Footer,
      onClick = SheetClosingClick(onContinue)
    ),
    secondaryButton = ButtonModel(
      text = "Set up later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onSetUpLater)
    ),
    renderContext = RenderContext.Sheet
  )
