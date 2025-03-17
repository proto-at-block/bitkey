package build.wallet.statemachine.dev

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface F8eCustomUrlStateMachine : StateMachine<F8eCustomUrlStateMachineProps, ScreenModel>

data class F8eCustomUrlStateMachineProps(
  val customUrl: String,
  val onBack: () -> Unit,
)
