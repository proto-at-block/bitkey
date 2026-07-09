package build.wallet.statemachine.trustedcontact

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.HeroFormBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory

data class PromoCodeUpsellBodyModel(
  override val onBack: () -> Unit,
  val promoCode: PromotionCode,
  val onClick: () -> Unit,
  val onContinue: () -> Unit,
  val onCopyCode: () -> Unit,
  val onShare: () -> Unit,
  val treatment: Treatment,
) : HeroFormBodyModel(
    id = InheritanceEventTrackerScreenId.PromoCodeUpsell,
    onBack = onBack,
    heroContent = HeroContent.PromoCodeHeader,
    leadingAccessory = CloseAccessory(onBack),
    headline = when (treatment) {
      is Treatment.ForBenefactor -> "Save 30% when you buy your beneficiary a Bitkey device"
      Treatment.ForBeneficiary -> "Get Bitkey now and save 30%"
    },
    subline = when (treatment) {
      is Treatment.ForBenefactor ->
        "${treatment.contactAlias} will need their own Bitkey to get set up " +
          "and accept your invite. Use the code below when checking out " +
          "to receive your unique 30% discount."
      Treatment.ForBeneficiary ->
        "You'll need a Bitkey to setup and use your account. " +
          "If your benefactor did not provide one, you can get yours now for 30% off."
    },
    callout = CalloutModel(
      title = promoCode.value,
      treatment = CalloutModel.Treatment.DefaultCentered,
      onTitleClick = StandardClick { onCopyCode() }
    ),
    primaryButton = ButtonModel(
      text = "Get a Bitkey",
      treatment = ButtonModel.Treatment.Primary,
      leadingIcon = Icon.ArrowUpRight,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onClick() }
    ),
    secondaryButton = ButtonModel(
      text = "Save code for later",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onShare() }
    ),
    scrollContent = true
  ) {
  sealed interface Treatment {
    data object ForBeneficiary : Treatment

    data class ForBenefactor(
      val contactAlias: String,
    ) : Treatment
  }
}
