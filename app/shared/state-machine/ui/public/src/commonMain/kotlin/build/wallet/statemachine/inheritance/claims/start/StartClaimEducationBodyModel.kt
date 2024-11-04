package build.wallet.statemachine.inheritance.claims.start

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Initial Education screen shown before starting a claim.
 */
data class StartClaimEducationBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.StartClaimEducationScreen,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "How Inheritance Works",
      subline = " • A claim cannot be canceled once submitted\n" +
        " • There will be a 6-month waiting period before funds are released\n" +
        " • After the waiting period, your funds will automatically transfer to your account"
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )
