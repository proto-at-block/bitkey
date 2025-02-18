package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class DeclineClaimBodyModel(
  val title: String,
  val subtitle: String,
  val dismiss: () -> Unit,
  val declineClaim: () -> Unit,
  val learnMore: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.DenyClaim,
    onBack = dismiss,
    renderContext = RenderContext.Screen,
    toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(dismiss)),
    header = FormHeaderModel(
      headline = title,
      subline = subtitle
    ),
    primaryButton = ButtonModel(
      text = "Decline claim",
      onClick = StandardClick {
        declineClaim()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    ),
    secondaryButton = ButtonModel(
      text = "Learn more",
      onClick = StandardClick {
        learnMore()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary
    )
  )
