package build.wallet.statemachine.trustedcontact

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class PromoCodeUpsellBodyModel(
  override val onBack: () -> Unit,
  val promoCode: PromotionCode,
  val onClick: () -> Unit,
  val onContinue: () -> Unit,
  val onCopyCode: () -> Unit,
  val onShare: () -> Unit,
  val treatment: Treatment,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.PromoCodeUpsell,
    onBack = onBack,
    disableFixedFooter = true,
    toolbar = ToolbarModel(
      heroContent = ToolbarModel.HeroContent.PromoCodeHeader,
      leadingAccessory = CloseAccessory(onBack)
    ),
    header = FormHeaderModel(
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
      }
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Callout(
        item = CalloutModel(
          title = promoCode.value,
          treatment = CalloutModel.Treatment.DefaultCentered,
          onTitleClick = StandardClick({
            onCopyCode()
          })
        )
      ),
      FormMainContentModel.Spacer()
    ),
    primaryButton = ButtonModel(
      text = "Get a Bitkey",
      treatment = ButtonModel.Treatment.Primary,
      leadingIcon = Icon.SmallIconArrowUpRight,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick({
        onClick()
      })
    ),
    secondaryButton = ButtonModel(
      text = "Save code for later",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick({
        onShare()
      })
    )
  ) {
  sealed interface Treatment {
    data object ForBeneficiary : Treatment

    data class ForBenefactor(
      val contactAlias: String,
    ) : Treatment
  }
}
