package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface TransactionDetailsUiStateMachine :
  StateMachine<TransactionDetailsUiProps, ScreenModel>

data class TransactionDetailsUiProps(
  val account: Account,
  val transaction: BitcoinTransaction,
  val onClose: () -> Unit,
)
