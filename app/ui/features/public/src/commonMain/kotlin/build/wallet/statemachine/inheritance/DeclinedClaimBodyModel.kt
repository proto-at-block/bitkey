package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class DeclinedClaimBodyModel(
  val dismiss: () -> Unit,
  val removeBeneficiary: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.DenyClaim,
    onBack = dismiss,
    renderContext = RenderContext.Screen,
    toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(dismiss)),
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.LargeIconCheckFilled,
        iconSize = IconSize.Avatar,
        iconTint = IconTint.Primary
      ),
      headline = "You're all set",
      subline = "This claim has been closed, and your Bitkey funds are secure."
    ),
    primaryButton = ButtonModel(
      text = "Done",
      onClick = StandardClick {
        dismiss()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    ),
    secondaryButton = ButtonModel(
      text = "Remove beneficiary",
      onClick = StandardClick {
        removeBeneficiary()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary
    )
  )
