package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.StateMachine

/**
 * Manages the state of the case when we have an active Lite Account ready to use.
 */
interface HasActiveLiteAccountDataStateMachine :
  StateMachine<HasActiveLiteAccountDataProps, AccountData>

data class HasActiveLiteAccountDataProps(
  val account: LiteAccount,
)
