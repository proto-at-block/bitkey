package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

interface PartnerTransactionItemUiStateMachine : StateMachine<PartnerTransactionItemUiProps, ListItemModel>

data class PartnerTransactionItemUiProps(
  val transaction: Transaction.PartnershipTransaction,
  val onClick: (transaction: Transaction.PartnershipTransaction) -> Unit,
)
