package build.wallet.statemachine.dev.debug

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for picking f8e environment to use for onboarding a new account.
 */
interface NetworkingDebugConfigPickerUiStateMachine : StateMachine<NetworkingDebugConfigProps, BodyModel>

data class NetworkingDebugConfigProps(
  val onExit: () -> Unit,
)
