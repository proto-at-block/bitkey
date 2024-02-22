package build.wallet.statemachine.dev.lightning

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

interface LightningSendReceiveUiStateMachine : StateMachine<LightningSendReceiveUiProps, BodyModel>

data class LightningSendReceiveUiProps(
  val onBack: () -> Unit,
)
