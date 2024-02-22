package build.wallet.statemachine.dev.lightning

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachine.Props

interface ConnectAndOpenChannelStateMachine : StateMachine<Props, BodyModel> {
  data class Props(
    val onBack: () -> Unit,
  )
}
