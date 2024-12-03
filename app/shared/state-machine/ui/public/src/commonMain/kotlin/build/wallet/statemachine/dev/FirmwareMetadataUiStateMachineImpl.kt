package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadataDao
import build.wallet.logging.*
import build.wallet.logging.logFailure
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.dev.FirmwareMetadataUiStateMachineImpl.State.LoadingMetadataUiState
import build.wallet.statemachine.dev.FirmwareMetadataUiStateMachineImpl.State.ReadMetadataSuccessUiState
import build.wallet.statemachine.dev.FirmwareMetadataUiStateMachineImpl.State.ReadingMetadataUiState
import build.wallet.statemachine.dev.FirmwareMetadataUiStateMachineImpl.State.ShowingMetadataUiState
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import com.github.michaelbull.result.onSuccess
import okio.ByteString.Companion.toByteString

class FirmwareMetadataUiStateMachineImpl(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val firmwareMetadataDao: FirmwareMetadataDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : FirmwareMetadataUiStateMachine {
  @Composable
  override fun model(props: FirmwareMetadataUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingMetadataUiState) }

    return when (val currentState = state) {
      LoadingMetadataUiState -> {
        LaunchedEffect("loading-firmware-metadata") {
          firmwareMetadataDao.activeFirmwareMetadata().collect { result ->
            result
              .onSuccess { metadata ->
                when (metadata) {
                  null -> logWarn { "No active metadata found" }
                  else -> state = ShowingMetadataUiState(firmwareMetadata = metadata)
                }
              }
              .logFailure { "Failed to read active metadata from db" }
          }
        }

        FirmwareMetadataBodyModel(
          onBack = props.onBack,
          onFirmwareMetadataRefreshClick = {
            state = ReadingMetadataUiState
          },
          firmwareMetadataModel = null
        ).asModalScreen()
      }

      is ReadMetadataSuccessUiState -> {
        LaunchedEffect("showing-read-firmware-success") {
          firmwareDeviceInfoDao.setDeviceInfo(currentState.firmwareInfoPair.first)
          firmwareMetadataDao.setFirmwareMetadata(currentState.firmwareInfoPair.second)
          state = LoadingMetadataUiState
        }

        LoadingBodyModel(
          message = "Loading metadata...",
          onBack = null,
          // This is in the debug menu
          id = null
        ).asModalScreen()
      }

      ReadingMetadataUiState -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val deviceInfo = commands.getDeviceInfo(session)
              val firmwareMetadata = commands.getFirmwareMetadata(session)
              Pair(deviceInfo, firmwareMetadata)
            },
            onSuccess = { fwInfoPair ->
              state = ReadMetadataSuccessUiState(fwInfoPair)
            },
            onCancel = { state = LoadingMetadataUiState },
            isHardwareFake = props.isHardwareFake,
            needsAuthentication = false,
            screenPresentationStyle = Modal,
            eventTrackerContext = METADATA
          )
        )
      }

      is ShowingMetadataUiState ->
        FirmwareMetadataBodyModel(
          onBack = props.onBack,
          onFirmwareMetadataRefreshClick = {
            state = ReadingMetadataUiState
          },
          firmwareMetadataModel = currentState.firmwareMetadata.toModel()
        ).asModalScreen()
    }
  }

  private fun FirmwareMetadata.toModel() =
    FirmwareMetadataModel(
      activeSlot = activeSlot.name,
      gitId = gitId,
      gitBranch = gitBranch,
      version = version,
      build = build,
      timestamp = timestamp.toString(),
      hash = hash.toByteArray().toByteString().hex(),
      hwRevision = hwRevision
    )

  private sealed class State {
    data class ShowingMetadataUiState(
      val firmwareMetadata: FirmwareMetadata,
    ) : State()

    data object LoadingMetadataUiState : State()

    data object ReadingMetadataUiState : State()

    data class ReadMetadataSuccessUiState(
      val firmwareInfoPair: Pair<FirmwareDeviceInfo, FirmwareMetadata>,
    ) : State()
  }
}
