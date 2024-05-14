package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.feature.FeatureFlagValue
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

class DoubleFlagItemUiStateMachineImpl : DoubleFlagItemUiStateMachine {
  @Composable
  override fun model(props: DoubleFlagItemUiProps): ListItemModel {
    val flagValue: FeatureFlagValue.DoubleFlag by remember(props.featureFlag.identifier) {
      props.featureFlag.flagValue()
    }.collectAsState()

    return ListItemModel(
      title = props.featureFlag.title,
      secondaryText = props.featureFlag.description,
      trailingAccessory = ListItemAccessory.TextAccessory(
        text = flagValue.value.toString()
      ),
      onClick = props.onClick
    )
  }
}
