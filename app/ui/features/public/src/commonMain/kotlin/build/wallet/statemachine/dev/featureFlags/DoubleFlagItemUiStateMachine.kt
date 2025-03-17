package build.wallet.statemachine.dev.featureFlags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagValue
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

interface DoubleFlagItemUiStateMachine : StateMachine<DoubleFlagItemUiProps, ListItemModel>

data class DoubleFlagItemUiProps(
  val featureFlag: FeatureFlag<FeatureFlagValue.DoubleFlag>,
  val onClick: () -> Unit,
)
