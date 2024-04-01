package build.wallet.statemachine.trustedcontact

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.crypto.PublicKey
import build.wallet.recovery.socrec.AcceptInvitationCodeError
import build.wallet.recovery.socrec.RetrieveInvitationCodeError
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

/**
 * A state machine for enrolling as a trusted contact for a customer.
 *
 * Note: this flow can be used for net-new customers, for already-established lite
 * customers, or for already-established full customers. It is the process to be
 * added as as trusted contact for some other full customer.
 *
 * It involves:
 * - Entering invite code (this step is optional and is skipped if the app is launched from
 *    a deeplink with the invite code embedded).
 * - Saving the name of the customer they are protecting
 */
interface TrustedContactEnrollmentUiStateMachine : StateMachine<TrustedContactEnrollmentUiProps, ScreenModel>

/**
 * @property retreat: Handles when the state machine requests to exit to its parent
 */
data class TrustedContactEnrollmentUiProps(
  val retreat: Retreat,
  val account: Account,
  val inviteCode: String?,
  val retrieveInvitation: suspend (
    String,
  ) -> Result<IncomingInvitation, RetrieveInvitationCodeError>,
  val acceptInvitation: suspend (
    IncomingInvitation,
    ProtectedCustomerAlias,
    PublicKey<DelegatedDecryptionKey>,
    String,
  ) -> Result<ProtectedCustomer, AcceptInvitationCodeError>,
  val screenPresentationStyle: ScreenPresentationStyle,
  val onDone: () -> Unit,
)
