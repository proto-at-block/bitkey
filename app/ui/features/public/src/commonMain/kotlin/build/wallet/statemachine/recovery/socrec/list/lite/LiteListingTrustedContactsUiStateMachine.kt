package build.wallet.statemachine.recovery.socrec.list.lite

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the screen model to show the 'Recovery Contacts' list UI
 * for Lite Accounts
 */
interface LiteListingTrustedContactsUiStateMachine :
  StateMachine<LiteListingTrustedContactsUiProps, ScreenModel>

data class LiteListingTrustedContactsUiProps(
  val account: Account,
  val onExit: () -> Unit,
  val onHelpWithRecovery: (ProtectedCustomer) -> Unit,
  val onAcceptInvitePressed: () -> Unit,
)
