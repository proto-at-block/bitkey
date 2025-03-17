package build.wallet.statemachine.dev.logs

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.dev.logs.LogsUiStateMachine.Props

/**
 * State machine that shows app's logs recorded by [LogStoreWriter]. Logs are stored in [LogStore].
 */
interface LogsUiStateMachine : StateMachine<Props, BodyModel> {
  data class Props(
    val onBack: () -> Unit,
  )
}
