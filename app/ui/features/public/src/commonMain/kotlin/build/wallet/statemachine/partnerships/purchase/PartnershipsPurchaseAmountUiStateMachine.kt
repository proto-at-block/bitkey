package build.wallet.statemachine.partnerships.purchase

import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for selecting the purchase amount for partnerships flow.
 */
interface PartnershipsPurchaseAmountUiStateMachine :
  StateMachine<PartnershipsPurchaseAmountUiProps, SheetModel>

/**
 * @param selectedAmount an initially selected amount
 * @param onAmountConfirmed called when the user confirms an amount
 * @param onSelectCustomAmount called when the user wants to enter a custom amount
 * @param onExit called to exit the purchase flow
 */
data class PartnershipsPurchaseAmountUiProps(
  val selectedAmount: FiatMoney?,
  val onAmountConfirmed: (FiatMoney) -> Unit,
  val onSelectCustomAmount: (min: FiatMoney, max: FiatMoney) -> Unit,
  val onExit: () -> Unit,
)
