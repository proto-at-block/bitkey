package build.wallet.statemachine.dev.lightning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.lightning.LightningPreference
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.coroutines.launch

class LightningOptionsUiStateMachineImpl(
  private val lightningPreference: LightningPreference,
) : LightningOptionsUiStateMachine {
  @Composable
  override fun model(props: LightningOptionsUiProps): ListGroupModel {
    var state by remember { mutableStateOf(State()) }

    LaunchedEffect("read-lightning-preference") {
      state = State(lightningEnabled = lightningPreference.get())
    }

    val scope = rememberStableCoroutineScope()

    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      items =
        immutableListOfNotNull(
          ListItemModel(
            title = "Run Lightning Node",
            trailingAccessory =
              SwitchAccessory(
                model =
                  SwitchModel(
                    checked = state.lightningEnabled,
                    enabled = false,
                    onCheckedChange = { enabled ->
                      scope.launch {
                        lightningPreference.set(enabled)
                        state = State(enabled)
                      }
                    }
                  )
              )
          ),
          when {
            state.lightningEnabled ->
              ListItemModel(
                title = "Lightning Node Options",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = props.onLightningOptionsClick
              )

            else -> null
          }
        )
    )
  }

  private data class State(
    val lightningEnabled: Boolean = false,
  )
}
