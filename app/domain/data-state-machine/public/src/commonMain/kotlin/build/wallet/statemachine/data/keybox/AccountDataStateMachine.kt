package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.StateMachine

/**
 * Manages [Account] data state.
 */
interface AccountDataStateMachine : StateMachine<AccountDataProps, AccountData>

/**
 * @property onLiteAccountCreated Callback to be invoked when a [LiteAccount] is created. This is
 * temporary since [LiteAccount]s are created in the [AccountDataStateMachine] but the Lite Money Home
 * screen is not rendered via [AccountData]
 */
data class AccountDataProps(
  val onLiteAccountCreated: (LiteAccount) -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)
