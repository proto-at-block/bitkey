package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.DEBUG
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachine
import build.wallet.statemachine.dev.analytics.Props
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsStateMachine
import build.wallet.statemachine.dev.lightning.LightningDebugMenuUiStateMachine
import build.wallet.statemachine.dev.lightning.LightningDebugMenuUiStateMachine.LightningDebugMenuUiProps
import build.wallet.statemachine.dev.logs.LogsUiStateMachine
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps

class DebugMenuStateMachineImpl(
  private val analyticsUiStateMachine: AnalyticsUiStateMachine,
  private val debugMenuListStateMachine: DebugMenuListStateMachine,
  private val f8eCustomUrlStateMachine: F8eCustomUrlStateMachine,
  private val featureFlagsStateMachine: FeatureFlagsStateMachine,
  private val firmwareMetadataUiStateMachine: FirmwareMetadataUiStateMachine,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val lightningDebugMenuUiStateMachine: LightningDebugMenuUiStateMachine,
  private val logsUiStateMachine: LogsUiStateMachine,
  private val networkingDebugConfigPickerUiStateMachine: NetworkingDebugConfigPickerUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
) : DebugMenuStateMachine {
  @Composable
  override fun model(props: DebugMenuProps): ScreenModel {
    var uiState: DebugMenuState by remember { mutableStateOf(DebugMenuState.ShowingDebugMenu) }

    return when (val state = uiState) {
      is DebugMenuState.ShowingDebugMenu ->
        debugMenuListStateMachine.model(
          props =
            DebugMenuListProps(
              accountData = props.accountData,
              firmwareData = props.firmwareData,
              onSetState = { uiState = it },
              onClose = props.onClose
            )
        ).asModalScreen()

      is DebugMenuState.ShowingF8eCustomUrl ->
        f8eCustomUrlStateMachine.model(
          F8eCustomUrlStateMachineProps(
            customUrl = state.customUrl,
            templateFullAccountConfigData = state.templateFullAccountConfigData,
            onBack = { uiState = DebugMenuState.ShowingDebugMenu }
          )
        )

      is DebugMenuState.ShowingLightningDebugMenu ->
        lightningDebugMenuUiStateMachine.model(
          LightningDebugMenuUiProps(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        )

      is DebugMenuState.ShowingLogs ->
        logsUiStateMachine.model(
          LogsUiStateMachine.Props(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

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
    val templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
  ) : DebugMenuState

  data object ShowingLogs : DebugMenuState

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

  data object ShowingLightningDebugMenu : DebugMenuState
}
