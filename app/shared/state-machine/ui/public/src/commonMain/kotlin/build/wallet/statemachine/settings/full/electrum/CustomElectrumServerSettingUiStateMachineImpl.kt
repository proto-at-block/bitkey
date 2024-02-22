package build.wallet.statemachine.settings.full.electrum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachineImpl.State.SettingCustomElectrumServerUiState
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachineImpl.State.ShowingCustomElectrumServerSettingsUiState

class CustomElectrumServerSettingUiStateMachineImpl(
  private val customElectrumServerUIStateMachine: CustomElectrumServerUiStateMachine,
  private val setElectrumServerUiStateMachine: SetElectrumServerUiStateMachine,
) : CustomElectrumServerSettingUiStateMachine {
  @Composable
  override fun model(props: CustomElectrumServerProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(ShowingCustomElectrumServerSettingsUiState)
    }

    return when (val currentState = state) {
      is ShowingCustomElectrumServerSettingsUiState ->
        ScreenModel(
          body =
            customElectrumServerUIStateMachine.model(
              props =
                CustomElectrumServerUiProps(
                  onBack = props.onBack,
                  electrumServerPreferenceValue = props.electrumServerPreferenceValue,
                  onAdjustElectrumServerClick = {
                    state =
                      when (val electrumServerPreferenceValue = props.electrumServerPreferenceValue) {
                        is On -> SettingCustomElectrumServerUiState(electrumServerPreferenceValue.server)
                        is Off -> SettingCustomElectrumServerUiState(electrumServerPreferenceValue.previousUserDefinedElectrumServer)
                      }
                  },
                  disableCustomElectrumServer = props.disableCustomElectrumServer
                )
            ),
          presentationStyle = Root
        )

      is SettingCustomElectrumServerUiState ->
        setElectrumServerUiStateMachine.model(
          props =
            SetElectrumServerProps(
              onClose = { state = ShowingCustomElectrumServerSettingsUiState },
              currentElectrumServerDetails = currentState.currentElectrumServer?.electrumServerDetails,
              onSetServer = { state = ShowingCustomElectrumServerSettingsUiState },
              activeNetwork = props.activeNetwork
            )
        )
    }
  }

  private sealed class State {
    /**
     * Showing current user setting for Electrum node it is connected to.
     */
    data object ShowingCustomElectrumServerSettingsUiState : State()

    /**
     * Showing screen where the user is setting the desired host and port for their Electrum server.
     */
    data class SettingCustomElectrumServerUiState(
      val currentElectrumServer: ElectrumServer?,
    ) : State()
  }
}
