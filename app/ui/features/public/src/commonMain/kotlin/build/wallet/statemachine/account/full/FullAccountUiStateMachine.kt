package build.wallet.statemachine.account.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData

/**
 * State machine for managing full account states including active accounts,
 * recovery conflicts, and loading states.
 */
interface FullAccountUiStateMachine : StateMachine<FullAccountUiProps, ScreenModel>

/**
 * Props for the full account state machine.
 *
 * @param accountData The current full account data state (can be checking, active, or recovery conflict)
 * @param isNewlyCreatedAccount Whether this is a newly created account (shows welcome screen)
 * @param isRenderingViaAccountData Whether we're rendering via account data flow (from onboarding)
 */
data class FullAccountUiProps(
  val accountData: AccountData,
  val isNewlyCreatedAccount: Boolean = false,
  val isRenderingViaAccountData: Boolean = false,
)
