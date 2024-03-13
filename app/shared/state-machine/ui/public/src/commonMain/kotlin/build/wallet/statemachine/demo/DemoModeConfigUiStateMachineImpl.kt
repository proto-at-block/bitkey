package build.wallet.statemachine.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.model.alert.DisableAlertModel

class DemoModeConfigUiStateMachineImpl(
  private val demoModeCodeEntryUiStateMachine: DemoModeCodeEntryUiStateMachineImpl,
) : DemoModeConfigUiStateMachine {
  @Composable
  override fun model(props: DemoModeConfigUiProps): ScreenModel {
    var uiState: ConfigState by remember { mutableStateOf(ConfigState.DemoModeDisabled) }

    return when (val state = uiState) {
      is ConfigState.DemoModeEnabled ->
        ScreenModel(
          body = DemoModeConfigBodyModel(
            onBack = props.onBack,
            switchIsChecked = true,
            onSwitchCheckedChange = { uiState = state.copy(confirmingCancellation = true) },
            disableAlertModel =
              when {
                state.confirmingCancellation -> {
                  disableDemoMode(
                    onConfirm = {
                      props.accountData.templateFullAccountConfigData.updateConfig {
                        it.copy(
                          isHardwareFake = false,
                          isTestAccount = false
                        )
                      }
                      uiState = ConfigState.DemoModeDisabled
                    },
                    onDismiss = {
                      uiState = state.copy(confirmingCancellation = false)
                    }
                  )
                }

                else -> null
              }
          )
        )

      is ConfigState.DemoModeDisabled ->
        ScreenModel(
          body = DemoModeConfigBodyModel(
            onBack = props.onBack,
            switchIsChecked = false,
            onSwitchCheckedChange = { uiState = ConfigState.DemoModeEntry },
            disableAlertModel = null
          )
        )

      is ConfigState.DemoModeEntry ->
        demoModeCodeEntryUiStateMachine.model(
          props = DemoCodeEntryUiProps(
            accountData = props.accountData,
            onCodeSuccess = {
              props.accountData.templateFullAccountConfigData.updateConfig {
                it.copy(
                  isHardwareFake = true,
                  isTestAccount = true
                )
              }
              uiState = ConfigState.DemoModeEnabled(confirmingCancellation = false)
            },
            onBack = { uiState = ConfigState.DemoModeDisabled }
          )
        )
    }
  }
}

private sealed class ConfigState {
  data class DemoModeEnabled(
    val confirmingCancellation: Boolean,
  ) : ConfigState()

  data object DemoModeDisabled : ConfigState()

  data object DemoModeEntry : ConfigState()
}

fun disableDemoMode(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) = DisableAlertModel(
  title = "Disable Demo Mode?",
  subline = "You will be return to the default settings once you hit “Disable”",
  onConfirm = onConfirm,
  onCancel = onDismiss
)
