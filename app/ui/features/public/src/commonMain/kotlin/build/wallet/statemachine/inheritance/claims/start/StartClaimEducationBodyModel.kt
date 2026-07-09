package build.wallet.statemachine.inheritance.claims.start

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.HeroFormBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory

/**
 * Initial Education screen shown before starting a claim.
 */
data class StartClaimEducationBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : HeroFormBodyModel(
    id = InheritanceEventTrackerScreenId.StartClaimEducationScreen,
    onBack = onBack,
    heroContent = HeroContent.InheritanceExplainer,
    leadingAccessory = CloseAccessory(onBack),
    headline = "How inheritance works",
    subline = "There will be a 6-month waiting period before funds are released.\n\n" +
      "After the waiting period, your funds will be available for transfer.",
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    )
  )
