package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.DEBUG
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachine
import build.wallet.statemachine.dev.analytics.Props
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsStateMachine
import build.wallet.statemachine.dev.logs.LogsUiStateMachine
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugProps
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugUiStateMachine
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps

class DebugMenuStateMachineImpl(
  private val analyticsUiStateMachine: AnalyticsUiStateMachine,
  private val bitcoinWalletDebugUiStateMachine: BitcoinWalletDebugUiStateMachine,
  private val debugMenuListStateMachine: DebugMenuListStateMachine,
  private val f8eCustomUrlStateMachine: F8eCustomUrlStateMachine,
  private val featureFlagsStateMachine: FeatureFlagsStateMachine,
  private val firmwareMetadataUiStateMachine: FirmwareMetadataUiStateMachine,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val logsUiStateMachine: LogsUiStateMachine,
  private val networkingDebugConfigPickerUiStateMachine: NetworkingDebugConfigPickerUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
  private val firmwareDataService: FirmwareDataService,
) : DebugMenuStateMachine {
  @Composable
  override fun model(props: DebugMenuProps): ScreenModel {
    var uiState: DebugMenuState by remember { mutableStateOf(DebugMenuState.ShowingDebugMenu) }

    val firmwareData = remember {
      firmwareDataService.firmwareData()
    }.collectAsState().value

    return when (val state = uiState) {
      is DebugMenuState.ShowingDebugMenu ->
        debugMenuListStateMachine.model(
          props =
            DebugMenuListProps(
              firmwareData = firmwareData,
              onSetState = { uiState = it },
              onClose = props.onClose
            )
        ).asModalScreen()

      is DebugMenuState.ShowingF8eCustomUrl ->
        f8eCustomUrlStateMachine.model(
          F8eCustomUrlStateMachineProps(
            customUrl = state.customUrl,
            onBack = { uiState = DebugMenuState.ShowingDebugMenu }
          )
        )

      is DebugMenuState.ShowingLogs ->
        logsUiStateMachine.model(
          LogsUiStateMachine.Props(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.ShowingBitcoinWalletDebugMenu ->
        bitcoinWalletDebugUiStateMachine.model(
          BitcoinWalletDebugProps(
            onBack = { uiState = DebugMenuState.ShowingDebugMenu }
          )
        ).asModalFullScreen()

      is DebugMenuState.ShowingAnalytics ->
        analyticsUiStateMachine.model(
          Props(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.ShowingFeatureFlags ->
        featureFlagsStateMachine.model(
          FeatureFlagsProps(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        )

      is DebugMenuState.ShowingCloudStorageDebugOptions ->
        cloudDevOptionsStateMachine.model(
          CloudDevOptionsProps(onExit = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.ShowingNetworkingDebugOptions ->
        networkingDebugConfigPickerUiStateMachine.model(
          NetworkingDebugConfigProps(onExit = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.UpdatingFirmware ->
        fwupNfcUiStateMachine.model(
          props =
            FwupNfcUiProps(
              firmwareData = state.firmwareData,
              isHardwareFake = state.isHardwareFake,
              onDone = { uiState = DebugMenuState.ShowingDebugMenu }
            )
        )

      is DebugMenuState.WipingHardware ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              if (!commands.wipeDevice(session)) {
                throw NfcException.UnknownError(message = "Failed to wipe device")
              }
            },
            onSuccess = { uiState = DebugMenuState.ShowingDebugMenu },
            onCancel = { uiState = DebugMenuState.ShowingDebugMenu },
            isHardwareFake = state.isHardwareFake,
            screenPresentationStyle = Modal,
            eventTrackerContext = DEBUG
          )
        )

      is DebugMenuState.ShowingFirmwareMetadata ->
        firmwareMetadataUiStateMachine.model(
          props =
            FirmwareMetadataUiProps(
              onBack = { uiState = DebugMenuState.ShowingDebugMenu },
              isHardwareFake = state.isHardwareFake
            )
        )
    }
  }
}

sealed interface DebugMenuState {
  data object ShowingDebugMenu : DebugMenuState

  data class ShowingF8eCustomUrl(
    val customUrl: String,
  ) : DebugMenuState

  data object ShowingLogs : DebugMenuState

  data object ShowingBitcoinWalletDebugMenu : DebugMenuState

  data object ShowingNetworkingDebugOptions : DebugMenuState

  data object ShowingCloudStorageDebugOptions : DebugMenuState

  data object ShowingAnalytics : DebugMenuState

  data object ShowingFeatureFlags : DebugMenuState

  data class UpdatingFirmware(
    val isHardwareFake: Boolean,
    val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : DebugMenuState

  data class WipingHardware(
    val isHardwareFake: Boolean,
  ) : DebugMenuState

  data class ShowingFirmwareMetadata(
    val isHardwareFake: Boolean,
  ) : DebugMenuState
}
