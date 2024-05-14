package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

interface TransactionDetailsUiStateMachine :
  StateMachine<TransactionDetailsUiProps, ScreenModel>

data class TransactionDetailsUiProps(
  val accountData: ActiveFullAccountLoadedData,
  val transaction: BitcoinTransaction,
  val onClose: () -> Unit,
)
