package build.wallet.statemachine.recovery.socrec.help

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * This state machine is used to show the flow of screens for an which is acting is a Recovery Contact
 * to recover another customer's account
 */
interface HelpingWithRecoveryUiStateMachine : StateMachine<HelpingWithRecoveryUiProps, ScreenModel>

/**
 * Props for the [HelpingWithRecoveryUiStateMachine]
 *
 * @property onExit - invoked once the flow has been dismissed
 */
data class HelpingWithRecoveryUiProps(
  val account: Account,
  val protectedCustomer: ProtectedCustomer,
  val onExit: () -> Unit,
)
