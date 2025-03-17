package build.wallet.statemachine.partnerships.sell

import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface PartnershipsSellUiStateMachine :
  StateMachine<PartnershipsSellUiProps, ScreenModel>

data class PartnershipsSellUiProps(
  val confirmedSale: ConfirmedPartnerSale? = null,
  val onBack: () -> Unit,
)

data class ConfirmedPartnerSale(
  val partner: PartnerId?,
  val event: PartnershipEvent?,
  val partnerTransactionId: PartnershipTransactionId?,
)
