package build.wallet.statemachine.settings.full.electrum

import androidx.compose.runtime.*
import build.wallet.bitcoin.sync.ElectrumConfigService
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerUiStateMachineImpl.State.CustomElectrumServerDisabledUiState
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerUiStateMachineImpl.State.CustomElectrumServerEnabledUiState
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow

@BitkeyInject(ActivityScope::class)
class CustomElectrumServerUiStateMachineImpl(
  private val electrumConfigService: ElectrumConfigService,
) : CustomElectrumServerUiStateMachine {
  @Composable
  override fun model(props: CustomElectrumServerUiProps): BodyModel {
    var state: State by remember(props) {
      mutableStateOf(
        State.stateFromElectrumServerPreferenceValue(
          props.electrumServerPreferenceValue
        )
      )
    }
    return when (val currentState = state) {
      is CustomElectrumServerDisabledUiState ->
        CustomElectrumServerBodyModel(
          onBack = props.onBack,
          switchIsChecked = false,
          electrumServerRow = null,
          disableAlertModel = null,
          onSwitchCheckedChange = {
            props.onAdjustElectrumServerClick()
          }
        )

      is CustomElectrumServerEnabledUiState -> {
        if (currentState.isDisablingCustomElectrumServer) {
          LaunchedEffect("clear-custom-electrum-server") {
            electrumConfigService.disableCustomElectrumServer()
            state = CustomElectrumServerDisabledUiState
          }
        }

        CustomElectrumServerBodyModel(
          onBack = props.onBack,
          switchIsChecked = true,
          electrumServerRow =
            ActionRow(
              title = "Connected to:",
              sideText = currentState.electrumServer.electrumServerDetails.url(),
              onClick = {
                props.onAdjustElectrumServerClick()
              }
            ),
          onSwitchCheckedChange = {
            state = currentState.copy(confirmingCancellation = true)
          },
          disableAlertModel =
            when {
              currentState.confirmingCancellation -> {
                disableCustomElectrumServerAlertModel(
                  onConfirm = {
                    state =
                      currentState.copy(
                        isDisablingCustomElectrumServer = true,
                        confirmingCancellation = false
                      )
                  },
                  onDismiss = {
                    state = currentState.copy(confirmingCancellation = false)
                  }
                )
              }

              else -> null
            }
        )
      }
    }
  }

  private sealed interface State {
    /**
     * User has enabled custom Electrum server, and we show them what they are connected to.
     *
     * @param electrumServer Electrum server the customer is connected to.
     * @param confirmingCancellation whether or not we show a confirmation alert when turning custom electrum servers off
     */
    data class CustomElectrumServerEnabledUiState(
      val electrumServer: ElectrumServer,
      val confirmingCancellation: Boolean,
      val isDisablingCustomElectrumServer: Boolean,
    ) : State

    /**
     * User is using the default Electrum server configuration.
     */
    data object CustomElectrumServerDisabledUiState : State

    companion object {
      /**
       * Convenience method for determining initial state based on Electrum server passed to the
       * state machine.
       */
      internal fun stateFromElectrumServerPreferenceValue(
        preferenceValue: ElectrumServerPreferenceValue?,
      ): State =
        when (preferenceValue) {
          // User has the custom electrum server setting turned off.
          is Off -> CustomElectrumServerDisabledUiState
          // User has the custom electrum server setting turned on.
          is On ->
            CustomElectrumServerEnabledUiState(
              electrumServer = preferenceValue.server,
              confirmingCancellation = false,
              isDisablingCustomElectrumServer = false
            )

          else -> CustomElectrumServerDisabledUiState
        }
    }
  }
}
