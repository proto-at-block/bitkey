package build.wallet.statemachine.partnerships.purchase

import build.wallet.bitkey.account.FullAccount
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod

/**
 * State machine used to purchase bitcoin from our partners
 */
interface PartnershipsPurchaseUiStateMachine : StateMachine<PartnershipsPurchaseUiProps, SheetModel>

/**
 * @param fiatCurrency - the fiat currency the user is purchasing in
 * @param selectedAmount - directly load quotes using a selected amount
 * @param onPartnerRedirected - used to redirect the user to a partner's widget or app
 * @param onBack - used to back out of purchase flow
 * @param onExit - used to exit the purchase flow
 */
data class PartnershipsPurchaseUiProps(
  val account: FullAccount,
  val fiatCurrency: FiatCurrency,
  val selectedAmount: FiatMoney?,
  val onPartnerRedirected: (PartnerRedirectionMethod) -> Unit,
  val onSelectCustomAmount: (FiatMoney, FiatMoney) -> Unit,
  val onBack: () -> Unit,
  val onExit: () -> Unit,
)
