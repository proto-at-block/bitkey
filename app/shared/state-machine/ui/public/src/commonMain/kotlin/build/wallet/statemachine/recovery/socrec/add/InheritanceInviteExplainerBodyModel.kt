package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Explainer screen shown before inviting a beneficiary.
 */
data class InheritanceInviteExplainerBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val learnMore: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_ADD_INHERITANCE_EXPLAINER,
    onBack = onBack,
    toolbar = ToolbarModel(
      heroContent = ToolbarModel.HeroContent.InheritanceExplainer,
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "How it works",
      subline = "Your beneficiary can initiate a transfer from the Bitkey app, which kicks off a 6-month" +
        " security period. At the end of the security period, your wallet will transfer your bitcoin to the" +
        " beneficiary's wallet.\n\n" +
        "Note that your recipient needs their own Bitkey to become a beneficiary."
    ),
    secondaryButton = ButtonModel(
      text = "Learn more",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(learnMore)
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )
