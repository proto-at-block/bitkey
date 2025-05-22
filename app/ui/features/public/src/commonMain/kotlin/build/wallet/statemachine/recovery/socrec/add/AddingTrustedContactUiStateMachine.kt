package build.wallet.statemachine.recovery.socrec.add

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

/**
 * State machine for adding a Recovery Contact. Manages generating and sending Invitation.
 */
interface AddingTrustedContactUiStateMachine : StateMachine<AddingTrustedContactUiProps, ScreenModel>

data class AddingTrustedContactUiProps(
  val account: FullAccount,
  val trustedContactRole: TrustedContactRole = TrustedContactRole.SocialRecoveryContact,
  val onAddTc: suspend (
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ) -> Result<OutgoingInvitation, Error>,
  val onInvitationShared: () -> Unit,
  val onExit: () -> Unit,
)
