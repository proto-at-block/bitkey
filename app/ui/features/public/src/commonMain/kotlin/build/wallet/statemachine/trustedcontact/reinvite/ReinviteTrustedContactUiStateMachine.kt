package build.wallet.statemachine.trustedcontact.reinvite

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for reinviting a Trusted Contact. Manages generating and sending Invitation.
 */
interface ReinviteTrustedContactUiStateMachine : StateMachine<ReinviteTrustedContactUiProps, ScreenModel>

data class ReinviteTrustedContactUiProps(
  val account: FullAccount,
  val trustedContactAlias: String,
  val relationshipId: String,
  val isBeneficiary: Boolean,
  val onExit: () -> Unit,
  val onSuccess: () -> Unit,
)
