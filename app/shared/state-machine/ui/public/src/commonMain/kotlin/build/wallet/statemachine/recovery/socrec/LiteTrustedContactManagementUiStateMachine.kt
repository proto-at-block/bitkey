package build.wallet.statemachine.recovery.socrec

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.recovery.socrec.SocRecTrustedContactActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for managing Trusted Contacts (syncing/viewing, adding, removing) for
 * customers with Lite Accounts. Lite accounts differ from Full Accounts in that they
 * can only *be* a Trusted Contact (i.e. view/add/remove customers they are protecting).
 * They cannot add a Trusted Contact.
 */
interface LiteTrustedContactManagementUiStateMachine :
  StateMachine<LiteTrustedContactManagementProps, ScreenModel>

data class LiteTrustedContactManagementProps(
  val accountData: AccountData.HasActiveLiteAccountData,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val actions: SocRecTrustedContactActions,
  val acceptInvite: AcceptInvite?,
  val onExit: () -> Unit,
) {
  data class AcceptInvite(val inviteCode: String?)
}
