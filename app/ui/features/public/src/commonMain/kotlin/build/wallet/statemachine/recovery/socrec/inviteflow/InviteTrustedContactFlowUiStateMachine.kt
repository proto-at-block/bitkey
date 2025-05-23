package build.wallet.statemachine.recovery.socrec.inviteflow

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for inviting a Recovery Contact. This state machine is responsible for launching the
 * experience from money home and manages the entire flow of [EducationUiStateMachine] and
 * [AddingTrustedContactUiStateMachine].
 */
interface InviteTrustedContactFlowUiStateMachine :
  StateMachine<InviteTrustedContactFlowUiProps, ScreenModel>

/**
 * @property fullAccountConfig - config from the current active keybox
 * @property fullAccountId - account id of the current active account inviting the Recovery Contact
 * @property onExit - callback invoked once the flow is exited
 */
data class InviteTrustedContactFlowUiProps(
  val account: FullAccount,
  val onExit: () -> Unit,
)
