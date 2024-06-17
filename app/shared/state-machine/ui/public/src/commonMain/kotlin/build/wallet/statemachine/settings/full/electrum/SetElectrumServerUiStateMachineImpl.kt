package build.wallet.statemachine.settings.full.electrum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.CustomElectrumServerEventTrackerScreenId
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.IncompatibleNetwork
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.Unreachable
import build.wallet.bitcoin.sync.ElectrumServer.Custom
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.time.Delayer
import com.github.michaelbull.result.mapBoth
import kotlin.time.Duration.Companion.seconds

class SetElectrumServerUiStateMachineImpl(
  private val delayer: Delayer,
  val electrumServerSettingProvider: ElectrumServerSettingProvider,
  val electrumReachability: ElectrumReachability,
) : SetElectrumServerUiStateMachine {
  @Composable
  override fun model(props: SetElectrumServerProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.DefiningElectrumServerUiState(props.currentElectrumServerDetails))
    }
    var hostString by remember { mutableStateOf(props.currentElectrumServerDetails?.host ?: "") }
    var portString by remember { mutableStateOf(props.currentElectrumServerDetails?.port ?: "") }

    val electrumServerDetails by remember(hostString, portString) {
      derivedStateOf { ElectrumServerDetails(hostString, portString) }
    }

    return when (val currentState = state) {
      is State.DefiningElectrumServerUiState ->
        SetElectrumServerModel(
          onClose = props.onClose,
          host = hostString,
          onHostStringChanged = {
            hostString = it
          },
          port = portString,
          onPortStringChanged = {
            portString = it
          },
          setServerButtonEnabled = hostString.isNotEmpty() && portString.isNotEmpty(),
          onSetServerClick = {
            state = State.SavingElectrumServerUiState(electrumServerDetails)
          }
        ).asModalScreen()

      is State.SavingElectrumServerUiState -> {
        LaunchedEffect("saving-electrum-server") {
          val electrumServerToSet = Custom(currentState.serverDetails)
          electrumReachability.reachable(electrumServerToSet, props.activeNetwork)
            .mapBoth(
              success = {
                electrumServerSettingProvider.setUserDefinedServer(electrumServerToSet)
                state = State.ElectrumServerIsSetUiState
              },
              failure = { error ->
                state =
                  when (error) {
                    is IncompatibleNetwork ->
                      State.SaveElectrumServerBadNetworkUiState(currentState.serverDetails)

                    is Unreachable -> State.SaveElectrumServerFailedUiState(currentState.serverDetails)
                  }
              }
            )
        }
        LoadingBodyModel(
          id = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_LOADING,
          message = "Saving Custom Electrum Server..."
        ).asModalScreen()
      }

      is State.ElectrumServerIsSetUiState -> {
        LaunchedEffect("custom-electrum-server-save-success") {
          delayer.delay(2.seconds)
          props.onSetServer()
        }
        SuccessBodyModel(
          id = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_SUCCESS,
          title = "Success",
          primaryButtonModel = null
        ).asModalScreen()
      }

      is State.SaveElectrumServerFailedUiState ->
        ErrorFormBodyModel(
          title = "Unable to contact Electrum server",
          subline = "Check your server host and port and try again.",
          primaryButton =
            ButtonDataModel(
              text = "Done",
              onClick = {
                state = State.DefiningElectrumServerUiState(currentState.serverDetails)
              }
            ),
          eventTrackerScreenId = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_ERROR
        ).asModalScreen()

      is State.SaveElectrumServerBadNetworkUiState ->
        ErrorFormBodyModel(
          title = "Incompatible Electrum server",
          subline = "Check your server host and port and try again.",
          primaryButton =
            ButtonDataModel(
              text = "Done",
              onClick = {
                state = State.DefiningElectrumServerUiState(currentState.serverDetails)
              }
            ),
          eventTrackerScreenId = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_ERROR
        ).asModalScreen()
    }
  }

  private sealed interface State {
    /**
     * First screen for setting a custom electrum server, user would need to define a host and port.
     *
     * @property electrumServerDetails: currently defined Electrum server.
     */
    data class DefiningElectrumServerUiState(
      val electrumServer: ElectrumServerDetails?,
    ) : State

    /**
     * Saving custom electrum server endpoint
     *
     * @property serverDetails: ElectrumServer that the user wants to use going forward.
     */
    data class SavingElectrumServerUiState(
      val serverDetails: ElectrumServerDetails,
    ) : State

    /**
     * Shown once we successfully save the user's electrum server preferences.
     */
    data object ElectrumServerIsSetUiState : State

    /**
     * Shown when we time out from attempting to contact the user's electrum server.
     * @property server: ElectrumServer that our user attempted to set, but could not be contacted.
     */
    data class SaveElectrumServerFailedUiState(
      val serverDetails: ElectrumServerDetails,
    ) : State

    /**
     * Shown when we try and connect to an Electrum endpoint that isn't serving the active keybox's
     * bitcoin network.
     */
    data class SaveElectrumServerBadNetworkUiState(
      val serverDetails: ElectrumServerDetails,
    ) : State
  }
}
