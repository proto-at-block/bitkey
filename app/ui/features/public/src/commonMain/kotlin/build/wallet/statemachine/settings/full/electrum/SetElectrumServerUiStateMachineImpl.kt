package build.wallet.statemachine.settings.full.electrum

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CustomElectrumServerEventTrackerScreenId
import build.wallet.bitcoin.sync.ElectrumReachability
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.IncompatibleNetwork
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.Unreachable
import build.wallet.bitcoin.sync.ElectrumServer.Custom
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.*
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.statemachine.settings.SettingsAppSegment
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.delay

@BitkeyInject(ActivityScope::class)
class SetElectrumServerUiStateMachineImpl(
  val electrumServerSettingProvider: ElectrumServerSettingProvider,
  val electrumReachability: ElectrumReachability,
  private val actionSuccessDuration: ActionSuccessDuration,
) : SetElectrumServerUiStateMachine {
  @Composable
  override fun model(props: SetElectrumServerProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.DefiningElectrumServerUiState(props.currentElectrumServerDetails))
    }
    var hostString by remember { mutableStateOf(props.currentElectrumServerDetails?.host.orEmpty()) }
    var portString by remember { mutableStateOf(props.currentElectrumServerDetails?.port.orEmpty()) }

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
          electrumReachability.reachable(electrumServerToSet)
            .mapBoth(
              success = {
                electrumServerSettingProvider.setUserDefinedServer(electrumServerToSet)
                state = State.ElectrumServerIsSetUiState
              },
              failure = { error ->
                state = State.SaveElectrumServerFailedUiState(currentState.serverDetails, error)
              }
            )
        }
        LoadingBodyModel(
          id = CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_LOADING,
          title = "Saving Custom Electrum Server..."
        ).asModalScreen()
      }

      is State.ElectrumServerIsSetUiState -> {
        LaunchedEffect("custom-electrum-server-save-success") {
          delay(actionSuccessDuration.value)
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
          title = currentState.error.errorTitle,
          subline = currentState.error.errorSubline,
          primaryButton =
            ButtonDataModel(
              text = "Done",
              onClick = {
                state = State.DefiningElectrumServerUiState(currentState.serverDetails)
              }
            ),
          errorData = ErrorData(
            segment = SettingsAppSegment.Electrum,
            actionDescription = "Saving custom Electrum server",
            cause = currentState.error
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
      val error: ElectrumReachabilityError,
    ) : State
  }
}

private val ElectrumReachabilityError.errorTitle: String
  get() =
    when (this) {
      is IncompatibleNetwork -> "Incompatible Electrum server"
      is Unreachable -> "Unable to contact Electrum server"
    }

private val ElectrumReachabilityError.errorSubline: String
  get() =
    when (this) {
      is IncompatibleNetwork -> INCOMPATIBLE_NETWORK_ERROR_SUBLINE
      is Unreachable -> "Check your server host and port and try again."
    }

private const val INCOMPATIBLE_NETWORK_ERROR_SUBLINE =
  "This Electrum server is connected to a different Bitcoin network. " +
    "Check the server network and try again."
