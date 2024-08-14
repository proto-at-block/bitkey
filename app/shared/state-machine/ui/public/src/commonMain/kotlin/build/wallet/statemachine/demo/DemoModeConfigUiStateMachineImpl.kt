package build.wallet.statemachine.demo

import androidx.compose.runtime.*
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.DebugOptionsService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.model.alert.DisableAlertModel
import kotlinx.coroutines.launch

class DemoModeConfigUiStateMachineImpl(
  private val demoModeCodeEntryUiStateMachine: DemoModeCodeEntryUiStateMachineImpl,
  private val debugOptionsService: DebugOptionsService,
) : DemoModeConfigUiStateMachine {
  @Composable
  override fun model(props: DemoModeConfigUiProps): ScreenModel {
    var uiState: ConfigState by remember { mutableStateOf(ConfigState.DemoModeDisabled) }
    val scope = rememberStableCoroutineScope()

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
                      scope.launch {
                        debugOptionsService.setIsHardwareFake(value = false)
                        debugOptionsService.setIsTestAccount(value = false)
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
              scope.launch {
                debugOptionsService.setIsHardwareFake(value = true)
                debugOptionsService.setIsTestAccount(value = true)
                uiState = ConfigState.DemoModeEnabled(confirmingCancellation = false)
              }
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
  subline = "You will be returned to the default settings once you hit “Disable”",
  onConfirm = onConfirm,
  onCancel = onDismiss
)
