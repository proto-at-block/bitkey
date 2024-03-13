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

fun MobilePayOnboardingScreenModel(
  onContinue: () -> Unit,
  onSetUpLater: () -> Unit,
  onClosed: () -> Unit,
) = FormBodyModel(
  id = MobilePayEventTrackerScreenId.ENABLE_MOBILE_PAY_INSTRUCTIONS,
  onBack = onClosed,
  toolbar = null,
  header =
    FormHeaderModel(
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
      headline = "Mobile pay",
      alignment = FormHeaderModel.Alignment.LEADING,
      subline =
        "Leave your device at home, and make small spends with just the key on your phone."
    ),
  primaryButton =
    ButtonModel(
      text = "Enable Mobile Pay",
      size = Footer,
      onClick = SheetClosingClick(onContinue)
    ),
  secondaryButton =
    ButtonModel(
      text = "Set up later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onSetUpLater)
    ),
  renderContext = RenderContext.Sheet
)
