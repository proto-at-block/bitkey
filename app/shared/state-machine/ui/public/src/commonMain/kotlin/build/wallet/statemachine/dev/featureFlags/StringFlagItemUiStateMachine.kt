package build.wallet.statemachine.dev.featureFlags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagValue
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListItemModel

interface StringFlagItemUiStateMachine : StateMachine<StringFlagItemUiProps, ListItemModel>

data class StringFlagItemUiProps(
  val featureFlag: FeatureFlag<FeatureFlagValue.StringFlag>,
  val onClick: () -> Unit,
)
