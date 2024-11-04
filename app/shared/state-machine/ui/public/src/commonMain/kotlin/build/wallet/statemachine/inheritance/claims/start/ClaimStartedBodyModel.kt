package build.wallet.statemachine.inheritance.claims.start

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * The claim has been successfully started.
 *
 * @param completeTime The end of the delay period, as a formatted date.
 */
internal data class ClaimStartedBodyModel(
  val completeTime: String,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ClaimSubmitted,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClose)
    ),
    header = FormHeaderModel(
      headline = "Inheritance claim submitted",
      subline = "The claim process has started. Your funds will be available for transfer on $completeTime. Keep notifications enabled to ensure you don’t miss important communication."
    ),
    primaryButton = ButtonModel(
      text = "Done",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onClose)
    )
  )
