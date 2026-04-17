package build.wallet.statemachine.account.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing full account states including active accounts,
 * recovery conflicts, and loading states.
 */
interface FullAccountUiStateMachine : StateMachine<FullAccountUiProps, ScreenModel>

/**
 * Props for the full account state machine.
 *
 * @param account The full account to manage
 * @param isNewlyCreatedAccount Whether this is a newly created account (shows welcome screen)
 */
data class FullAccountUiProps(
  val account: FullAccount,
  val isNewlyCreatedAccount: Boolean = false,
)
