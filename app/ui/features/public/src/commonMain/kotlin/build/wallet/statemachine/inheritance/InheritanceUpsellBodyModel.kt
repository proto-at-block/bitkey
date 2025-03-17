package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.BackgroundTreatment
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class InheritanceUpsellBodyModel(
  val onGetStarted: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.Upsell,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconX,
            iconSize = IconSize.Accessory,
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Regular,
              color = IconBackgroundType.Circle.CircleColor.Dark
            ),
            iconTint = IconTint.OnTranslucent
          ),
          onClick = StandardClick { onClose() }
        )
      )
    ),
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(icon = Icon.InheritanceShowcase),
        title = "BITKEY INHERITANCE",
        body = LabelModel.StringModel("Safeguard the future of your bitcoin."),
        treatment = FormMainContentModel.Showcase.Treatment.INHERITANCE
      )
    ),
    primaryButton = ButtonModel(
      text = "Get started",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Accent,
      onClick = StandardClick(onGetStarted)
    ),
    secondaryButton = ButtonModel(
      text = "Set up later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.White,
      onClick = StandardClick(onClose)
    ),
    backgroundTreatment = BackgroundTreatment.Inheritance
  )

fun InheritanceUpsellSheetModel(
  onGetStarted: () -> Unit,
  onClose: () -> Unit,
) = SheetModel(
  body = InheritanceUpsellBodyModel(
    onGetStarted = onGetStarted,
    onClose = onClose
  ),
  treatment = SheetTreatment.INHERITANCE,
  size = SheetSize.FULL,
  onClosed = onClose
)
