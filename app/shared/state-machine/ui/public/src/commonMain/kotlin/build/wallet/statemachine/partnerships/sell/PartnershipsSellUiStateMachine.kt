package build.wallet.statemachine.partnerships.sell

import build.wallet.bitkey.account.FullAccount
import build.wallet.partnerships.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface PartnershipsSellUiStateMachine :
  StateMachine<PartnershipsSellUiProps, ScreenModel>

data class PartnershipsSellUiProps(
  val account: FullAccount,
  val confirmedSale: ConfirmedPartnerSale? = null,
  val onBack: () -> Unit,
)

data class ConfirmedPartnerSale(
  val partner: PartnerId?,
  val event: PartnershipEvent?,
  val partnerTransactionId: PartnershipTransactionId?,
)
