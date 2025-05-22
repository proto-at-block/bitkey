package build.wallet.statemachine.trustedcontact.view

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for viewing the details and actions on an established Recovery Contact.
 */
interface ViewingRecoveryContactUiStateMachine :
  StateMachine<ViewingRecoveryContactProps, ScreenModel>

data class ViewingRecoveryContactProps(
  val screenBody: BodyModel,
  val recoveryContact: TrustedContact,
  val account: FullAccount,
  val afterContactRemoved: (TrustedContact) -> Unit,
  val onExit: () -> Unit,
)
