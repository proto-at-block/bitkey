package build.wallet.statemachine.partnerships.transfer

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod

interface PartnershipsTransferUiStateMachine : StateMachine<PartnershipsTransferUiProps, SheetModel>

data class PartnershipsTransferUiProps(
  val account: FullAccount,
  val onBack: () -> Unit,
  val onAnotherWalletOrExchange: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod) -> Unit,
  val onExit: () -> Unit,
)
