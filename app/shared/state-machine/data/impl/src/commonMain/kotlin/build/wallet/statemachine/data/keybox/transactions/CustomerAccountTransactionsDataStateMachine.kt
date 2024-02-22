package build.wallet.statemachine.data.keybox.transactions

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for accessing and managing Full Account's balance and transaction history.
 */
interface FullAccountTransactionsDataStateMachine : StateMachine<FullAccountTransactionsDataProps, FullAccountTransactionsData>

data class FullAccountTransactionsDataProps(
  val account: FullAccount,
  val wallet: SpendingWallet,
)
