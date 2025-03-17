package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for inviting a beneficiary. This state machine is responsible for launching the
 * screens for education and inputting information for inviting a beneficiary
 */
interface InviteBeneficiaryUiStateMachine :
  StateMachine<InviteBeneficiaryUiProps, ScreenModel>

/**
 * @property account - current active account inviting the beneficiary
 * @property onExit - callback invoked if the flow is exited early
 * @property onInvited - callback invoked when the flow has completed after sending an invite
 */
data class InviteBeneficiaryUiProps(
  val account: FullAccount,
  val onExit: () -> Unit,
  val onInvited: () -> Unit,
)
