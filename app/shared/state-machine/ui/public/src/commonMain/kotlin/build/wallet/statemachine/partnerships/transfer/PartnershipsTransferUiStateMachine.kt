package build.wallet.statemachine.partnerships.transfer

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

interface PartnershipsTransferUiStateMachine : StateMachine<PartnershipsTransferUiProps, SheetModel>

data class PartnershipsTransferUiProps(
  val account: FullAccount,
  val keybox: Keybox,
  val sellBitcoinEnabled: Boolean,
  val onBack: () -> Unit,
  val onAnotherWalletOrExchange: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod, PartnershipTransaction) -> Unit,
  val onExit: () -> Unit,
)
