package build.wallet.statemachine.dev.analytics

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that shows app's analytics logs stored in [EventStore].
 */
interface AnalyticsUiStateMachine : StateMachine<Props, BodyModel>

data class Props(
  val onBack: () -> Unit,
)
