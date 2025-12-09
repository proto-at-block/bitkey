package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine

/**
 * Manages [Account] data state for active accounts only.
 */
interface AccountDataStateMachine : StateMachine<AccountDataProps, AccountData>

/**
 * Props for AccountDataStateMachine - no longer handles NoActiveAccountData cases.
 */
data class AccountDataProps(
  val account: FullAccount,
)
