package build.wallet.statemachine.dev.featureFlags

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for deciding which feature flag related options to show on the
 * main debug menu
 */
interface FeatureFlagsOptionsUiStateMachine : StateMachine<FeatureFlagsOptionsUiProps, ListGroupModel?>

data class FeatureFlagsOptionsUiProps(
  val onShowFeatureFlags: () -> Unit,
)
