package build.wallet.statemachine.trustedcontact

import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that shows a screen with the promo code upsell.
 */
interface PromoCodeUpsellUiStateMachine : StateMachine<PromoCodeUpsellUiProps, ScreenModel>

data class PromoCodeUpsellUiProps(
  val promoCode: PromotionCode,
  val contactAlias: String? = null,
  val onExit: () -> Unit,
)
