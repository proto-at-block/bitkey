package build.wallet.statemachine.dev.featureFlags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

/**
 * State machine for the UI for an individual feature flag with boolean values
 */
interface BooleanFlagItemUiStateMachine : StateMachine<BooleanFlagItemUiProps, ListItemModel>

data class BooleanFlagItemUiProps(
  val featureFlag: FeatureFlag<BooleanFlag>,
  val onShowAlertMessage: (message: String) -> Unit,
)
