package build.wallet.statemachine.inheritance.claims.start

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Screen asking the user to confirm their intent to start a claim.
 */
data class StartClaimConfirmationBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.SubmitClaimPromptScreen,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Submit inheritance claim",
      subline = "Keep notifications enabled to ensure you receive important communication during the process."
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBack)
    )
  )
