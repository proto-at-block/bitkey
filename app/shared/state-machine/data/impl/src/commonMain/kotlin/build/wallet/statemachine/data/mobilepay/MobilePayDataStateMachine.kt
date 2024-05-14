package build.wallet.statemachine.data.mobilepay

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData

interface MobilePayDataStateMachine : StateMachine<MobilePayProps, MobilePayData>

data class MobilePayProps(
  val account: FullAccount,
  val transactionsData: FullAccountTransactionsLoadedData,
)
