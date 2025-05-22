package build.wallet.statemachine.trustedcontact.remove

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for removing an existing or pending Recovery Contact.
 */
interface RemoveTrustedContactUiStateMachine :
  StateMachine<RemoveTrustedContactUiProps, ScreenModel>

data class RemoveTrustedContactUiProps(
  val account: FullAccount,
  val trustedContact: TrustedContact,
  val onClosed: () -> Unit,
)
