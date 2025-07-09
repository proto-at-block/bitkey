package build.wallet.statemachine.recovery.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing Trusted Contacts (syncing/viewing, adding, removing) for
 * customers with Lite Accounts. Lite accounts differ from Full Accounts in that they
 * can only *be* a Trusted Contact (i.e. view/add/remove customers they are protecting).
 * They cannot add a Trusted Contact.
 */
interface LiteTrustedContactManagementUiStateMachine :
  StateMachine<LiteTrustedContactManagementProps, ScreenModel>

data class LiteTrustedContactManagementProps(
  val account: LiteAccount,
  val acceptInvite: AcceptInvite?,
  val onAccountUpgraded: (FullAccount) -> Unit,
  val onExit: () -> Unit,
) {
  data class AcceptInvite(val inviteCode: String?)
}
