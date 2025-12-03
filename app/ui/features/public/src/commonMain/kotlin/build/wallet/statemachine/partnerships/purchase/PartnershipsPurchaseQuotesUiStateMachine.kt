package build.wallet.statemachine.partnerships.purchase

import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for displaying and selecting partner purchase quotes.
 * Returns a full [ScreenModel] for the partner selection experience.
 */
interface PartnershipsPurchaseQuotesUiStateMachine :
  StateMachine<PartnershipsPurchaseQuotesUiProps, ScreenModel>

/**
 * @param purchaseAmount the amount to purchase
 * @param onPartnerRedirected called when the user has selected a partner and is being redirected
 * @param onBack called when the user wants to go back to amount selection
 * @param onExit called when the user wants to exit the entire purchase flow
 */
data class PartnershipsPurchaseQuotesUiProps(
  val purchaseAmount: FiatMoney,
  val onPartnerRedirected: (PartnerRedirectionMethod, PartnershipTransaction) -> Unit,
  val onBack: () -> Unit,
  val onExit: () -> Unit,
)
