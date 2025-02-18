package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class BeneficiaryOnboardingBodyModel(
  override val onBack: () -> Unit = { },
  val onContinue: () -> Unit = { },
  val onMoreInfo: () -> Unit = { },
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ONBOARDING,
    onBack = onBack,
    toolbar = ToolbarModel(
      heroContent = ToolbarModel.HeroContent.InheritanceSetup,
      leadingAccessory =
        BackAccessory(
          onClick = onBack
        )
    ),
    header = FormHeaderModel(
      headline = "You’ve been invited to be a beneficiary",
      subline = "A contact of yours has invited you to be the beneficiary of their Bitkey wallet. Accept the invite to get set up."
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    ),
    secondaryButton = ButtonModel(
      text = "Learn more",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onMoreInfo)
    )
  )
