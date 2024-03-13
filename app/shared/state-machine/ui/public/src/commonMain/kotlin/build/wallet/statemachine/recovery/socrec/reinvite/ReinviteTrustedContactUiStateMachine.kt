package build.wallet.statemachine.recovery.socrec.reinvite

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

/**
 * State machine for reinviting a Trusted Contact. Manages generating and sending Invitation.
 */
interface ReinviteTrustedContactUiStateMachine : StateMachine<ReinviteTrustedContactUiProps, ScreenModel>

data class ReinviteTrustedContactUiProps(
  val account: FullAccount,
  val trustedContactAlias: String,
  val relationshipId: String,
  val onReinviteTc: suspend (HwFactorProofOfPossession) -> Result<OutgoingInvitation, Error>,
  val onExit: () -> Unit,
)
