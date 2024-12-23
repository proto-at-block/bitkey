package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

@BitkeyInject(ActivityScope::class)
class StringFlagItemUiStateMachineImpl : StringFlagItemUiStateMachine {
  @Composable
  override fun model(props: StringFlagItemUiProps): ListItemModel {
    val flagValue by remember(props.featureFlag.identifier) {
      props.featureFlag.flagValue()
    }.collectAsState()

    return ListItemModel(
      title = props.featureFlag.title,
      secondaryText = props.featureFlag.description,
      trailingAccessory =
        ListItemAccessory.TextAccessory(
          text = flagValue.value
        ),
      onClick = props.onClick
    )
  }
}
