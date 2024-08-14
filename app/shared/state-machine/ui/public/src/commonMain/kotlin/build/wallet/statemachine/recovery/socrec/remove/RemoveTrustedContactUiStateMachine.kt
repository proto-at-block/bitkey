package build.wallet.statemachine.recovery.socrec.remove

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

/**
 * State machine for removing an existing or pending Trusted Contact.
 */
interface RemoveTrustedContactUiStateMachine : StateMachine<RemoveTrustedContactUiProps, ScreenModel>

data class RemoveTrustedContactUiProps(
  val account: FullAccount,
  val trustedContact: TrustedContact,
  val onRemoveTrustedContact: suspend (HwFactorProofOfPossession?) -> Result<Unit, Error>,
  val onClosed: () -> Unit,
)
