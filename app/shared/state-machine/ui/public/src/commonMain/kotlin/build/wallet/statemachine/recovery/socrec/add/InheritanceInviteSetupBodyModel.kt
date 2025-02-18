package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Education screen shown before inviting a beneficiary.
 */
data class InheritanceInviteSetupBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val learnMore: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_ADD_INHERITANCE_SETUP,
    onBack = onBack,
    toolbar = ToolbarModel(
      heroContent = ToolbarModel.HeroContent.InheritanceSetup,
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Setting up inheritance",
      subline = "Setup is simple — choose a beneficiary to receive the bitcoin held in your wallet" +
        " and send them an invite.\n\n" +
        "You can change or remove your beneficiary by going to Inheritance in Settings."
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
