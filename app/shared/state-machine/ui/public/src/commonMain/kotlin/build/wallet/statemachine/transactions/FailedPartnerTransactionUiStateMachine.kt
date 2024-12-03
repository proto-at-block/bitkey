package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface FailedPartnerTransactionUiStateMachine : StateMachine<FailedPartnerTransactionProps, ScreenModel>

data class FailedPartnerTransactionProps(
  val transaction: Transaction.PartnershipTransaction,
  val onClose: () -> Unit,
)
