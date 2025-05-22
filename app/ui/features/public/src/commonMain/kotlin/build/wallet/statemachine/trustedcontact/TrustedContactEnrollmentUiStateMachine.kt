package build.wallet.statemachine.trustedcontact

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant

/**
 * A state machine for enrolling as a Recovery Contact for a customer.
 *
 * Note: this flow can be used for net-new customers, for already-established lite
 * customers, or for already-established full customers. It is the process to be
 * added as as Recovery Contact for some other full customer.
 *
 * It involves:
 * - Entering invite code (this step is optional and is skipped if the app is launched from
 *    a deeplink with the invite code embedded).
 * - Saving the name of the customer they are protecting
 */
interface TrustedContactEnrollmentUiStateMachine :
  StateMachine<TrustedContactEnrollmentUiProps, ScreenModel>

/**
 * @property retreat: Handles when the state machine requests to exit to its parent
 */
data class TrustedContactEnrollmentUiProps(
  val retreat: Retreat,
  val account: Account,
  val inviteCode: String?,
  val screenPresentationStyle: ScreenPresentationStyle,
  val onDone: (Account) -> Unit,
  val variant: TrustedContactFeatureVariant,
)
