package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

class BooleanFlagItemUiStateMachineImpl : BooleanFlagItemUiStateMachine {
  @Composable
  override fun model(props: BooleanFlagItemUiProps): ListItemModel {
    val flagValue by remember(props.featureFlag.identifier) {
      props.featureFlag.flagValue()
    }.collectAsState()

    val scope = rememberStableCoroutineScope()

    return ListItemModel(
      title = props.featureFlag.title,
      secondaryText = props.featureFlag.description,
      trailingAccessory =
        SwitchAccessory(
          model =
            SwitchModel(
              checked = flagValue.value,
              onCheckedChange = { newValue ->
                scope.launch {
                  props.featureFlag.canSetValue(BooleanFlag(newValue))
                    .onSuccess {
                      props.featureFlag.setFlagValue(BooleanFlag(newValue))
                    }
                    .onFailure { alertMessage ->
                      props.onShowAlertMessage(alertMessage)
                    }
                }
              }
            )
        )
    )
  }
}
