package build.wallet.statemachine.transactions

import build.wallet.activity.Transaction
import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Responsible for showing details of an on-chain bitcoin transaction.
 */
interface TransactionDetailsUiStateMachine : StateMachine<TransactionDetailsUiProps, ScreenModel>

data class TransactionDetailsUiProps(
  val account: Account,
  val transaction: Transaction,
  val onClose: () -> Unit,
)
