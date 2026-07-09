package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType

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
            icon = Icon.X,
            iconSize = IconSize.Accessory,
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Regular,
              color = IconBackgroundType.Circle.CircleColor.Dark
            ),
            iconTint = IconTint.OnTranslucent
          ),
          testTag = "close-button",
          onClick = StandardClick { onClose() }
        )
      )
    ),
    header = null,
    formScreenLayout = FormScreenLayoutModel.LargeTitle(),
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(icon = Icon.InheritanceShowcase),
        title = null,
        body = null,
        fillAvailableSpace = false
      )
    ),
    preFooterContentList = immutableListOf(
      FormMainContentModel.HeaderBlock(
        header = FormHeaderModel(
          headline = "Bitkey Inheritance",
          subline = "Safeguard the future of your bitcoin.",
          headlineLabelType = LabelType.Display3
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Get started",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onGetStarted)
    ),
    secondaryButton = ButtonModel(
      text = "Set up later",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onClose)
    )
  )

fun InheritanceUpsellSheetModel(
  onGetStarted: () -> Unit,
  onClose: () -> Unit,
) = SheetModel(
  body = InheritanceUpsellBodyModel(
    onGetStarted = onGetStarted,
    onClose = onClose
  ),
  size = SheetSize.FULL,
  onClosed = onClose
)
