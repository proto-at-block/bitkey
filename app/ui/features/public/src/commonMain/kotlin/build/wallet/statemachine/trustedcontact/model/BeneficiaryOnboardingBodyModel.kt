package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.form.HeroFormBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory

data class BeneficiaryOnboardingBodyModel(
  override val onBack: () -> Unit = { },
  val onContinue: () -> Unit = { },
  val onMoreInfo: () -> Unit = { },
) : HeroFormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ONBOARDING,
    onBack = onBack,
    heroContent = HeroContent.InheritanceSetup,
    leadingAccessory = BackAccessory(onClick = onBack),
    headline = "You’ve been invited to be a beneficiary",
    subline = "A contact of yours has invited you to be the beneficiary of their Bitkey wallet. " +
      "Accept the invite to get set up.",
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
