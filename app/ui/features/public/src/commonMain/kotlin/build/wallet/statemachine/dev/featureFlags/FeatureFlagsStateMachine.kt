package build.wallet.statemachine.dev.featureFlags

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for viewing and updating the list of feature flags
 */
interface FeatureFlagsStateMachine : StateMachine<FeatureFlagsProps, ScreenModel>

data class FeatureFlagsProps(
  val onBack: () -> Unit,
)
