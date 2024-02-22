package build.wallet.statemachine.settings.full.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProvider
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.HardwareRecoveryDelayAndNotifyUiState
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.InitiatingHardwareRecoveryUiState
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.TappingForFirmwareMetadataUiState
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.UpdatingFirmwareUiState
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.ViewingDeviceDataUiState
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.DurationFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.ui.model.alert.AlertModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

class DeviceSettingsUiStateMachineImpl(
  private val lostHardwareRecoveryUiStateMachine: LostHardwareRecoveryUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val durationFormatter: DurationFormatter,
  private val appFunctionalityStatusProvider: AppFunctionalityStatusProvider,
) : DeviceSettingsUiStateMachine {
  @Composable
  override fun model(props: DeviceSettingsProps): ScreenModel {
    var uiState: DeviceSettingsUiState by remember {
      mutableStateOf(ViewingDeviceDataUiState)
    }

    var alertModel: AlertModel? by remember { mutableStateOf(null) }

    val appFunctionalityStatus by remember {
      appFunctionalityStatusProvider.appFunctionalityStatus(
        props.accountData.account.config.f8eEnvironment
      )
    }.collectAsState(AppFunctionalityStatus.FullFunctionality)

    val securityAndRecoveryStatus by remember {
      derivedStateOf {
        appFunctionalityStatus.featureStates.securityAndRecovery
      }
    }

    return when (val state = uiState) {
      is ViewingDeviceDataUiState ->
        props.firmwareData.firmwareDeviceInfo?.let { deviceInfo ->
          ViewingDeviceScreenModel(
            props = props,
            firmwareDeviceInfo = deviceInfo,
            goToFwup = { uiState = UpdatingFirmwareUiState(it) },
            goToNfcMetadata = { uiState = TappingForFirmwareMetadataUiState },
            goToRecovery = {
              if (securityAndRecoveryStatus == FunctionalityFeatureStates.FeatureState.Available) {
                uiState = InitiatingHardwareRecoveryUiState
              } else {
                alertModel =
                  AppFunctionalityStatusAlertModel(
                    status = appFunctionalityStatus as AppFunctionalityStatus.LimitedFunctionality,
                    onDismiss = { alertModel = null }
                  )
              }
            },
            onManageReplacement = { uiState = HardwareRecoveryDelayAndNotifyUiState },
            dateTimeFormatter = dateTimeFormatter,
            timeZoneProvider = timeZoneProvider,
            durationFormatter = durationFormatter,
            replaceDeviceEnabled = securityAndRecoveryStatus == FunctionalityFeatureStates.FeatureState.Available
          ).copy(
            alertModel = alertModel
          )
        } ?: run {
          ErrorFormBodyModel(
            title = "There was an issue retrieving your device information",
            primaryButton =
              ButtonDataModel(
                text = "Go Back",
                onClick = props.onBack
              ),
            eventTrackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO_ERROR
          ).asRootScreen()
        }

      InitiatingHardwareRecoveryUiState ->
        lostHardwareRecoveryUiStateMachine.model(
          props =
            LostHardwareRecoveryProps(
              keyboxConfig = props.accountData.account.keybox.config,
              fullAccountId = props.accountData.account.accountId,
              lostHardwareRecoveryData = props.accountData.lostHardwareRecoveryData,
              fiatCurrency = props.fiatCurrency,
              screenPresentationStyle = Modal,
              instructionsStyle = InstructionsStyle.Independent,
              onFoundHardware = {}, // noop
              onExit = { uiState = ViewingDeviceDataUiState }
            )
        )

      HardwareRecoveryDelayAndNotifyUiState ->
        lostHardwareRecoveryUiStateMachine.model(
          props =
            LostHardwareRecoveryProps(
              keyboxConfig = props.accountData.account.keybox.config,
              fullAccountId = props.accountData.account.accountId,
              lostHardwareRecoveryData = props.accountData.lostHardwareRecoveryData,
              fiatCurrency = props.fiatCurrency,
              screenPresentationStyle = Modal,
              instructionsStyle = InstructionsStyle.Independent,
              onFoundHardware = {}, // noop
              onExit = { uiState = ViewingDeviceDataUiState }
            )
        )

      TappingForFirmwareMetadataUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              firmwareDeviceInfoDao.setDeviceInfo(
                commands.getDeviceInfo(session)
              )
            },
            onSuccess = { uiState = ViewingDeviceDataUiState },
            onCancel = { uiState = ViewingDeviceDataUiState },
            isHardwareFake = props.accountData.account.config.isHardwareFake,
            needsAuthentication = false,
            screenPresentationStyle = Modal,
            eventTrackerContext = METADATA
          )
        )

      is UpdatingFirmwareUiState ->
        fwupNfcUiStateMachine.model(
          props =
            FwupNfcUiProps(
              firmwareData = state.pendingFirmwareUpdate,
              isHardwareFake = props.accountData.account.config.isHardwareFake,
              onDone = { uiState = ViewingDeviceDataUiState }
            )
        )
    }
  }
}

@Composable
private fun ViewingDeviceScreenModel(
  props: DeviceSettingsProps,
  firmwareDeviceInfo: FirmwareDeviceInfo,
  goToFwup: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
  goToNfcMetadata: () -> Unit,
  goToRecovery: () -> Unit,
  onManageReplacement: () -> Unit,
  dateTimeFormatter: DateTimeFormatter,
  timeZoneProvider: TimeZoneProvider,
  durationFormatter: DurationFormatter,
  replaceDeviceEnabled: Boolean,
): ScreenModel {
  return ScreenModel(
    body =
      DeviceSettingsFormBodyModel(
        currentVersion = firmwareDeviceInfo.version,
        updateVersion = props.firmwareData.updateVersion,
        modelNumber = firmwareDeviceInfo.hwRevision,
        serialNumber = firmwareDeviceInfo.serial,
        // trim decimals and format as int
        deviceCharge = "${firmwareDeviceInfo.batteryChargeForUninitializedModelGauge()}%",
        lastSyncDate =
          dateTimeFormatter.fullShortDateWithTime(
            localDateTime =
              Instant.fromEpochSeconds(firmwareDeviceInfo.timeRetrieved)
                .toLocalDateTime(timeZoneProvider.current())
          ),
        replaceDeviceEnabled = replaceDeviceEnabled,
        replacementPending =
          when (val recoveryData = props.accountData.lostHardwareRecoveryData) {
            is LostHardwareRecoveryInProgressData ->
              when (val recoveryInProgressData = recoveryData.recoveryInProgressData) {
                is WaitingForRecoveryDelayPeriodData ->
                  durationFormatter.formatWithWords(
                    nonNegativeDurationBetween(
                      startTime = Clock.System.now(),
                      endTime = recoveryInProgressData.delayPeriodEndTime
                    )
                  )
                is CompletingRecoveryData -> "Awaiting confirmation"
                else -> null
              }
            else -> null
          },
        onUpdateVersion =
          when (val firmwareUpdateState = props.firmwareData.firmwareUpdateState) {
            is FirmwareData.FirmwareUpdateState.UpToDate -> null
            is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
              { goToFwup(firmwareUpdateState) }
            }
          },
        onSyncDeviceInfo = { goToNfcMetadata() },
        onReplaceDevice = goToRecovery,
        onManageReplacement = { onManageReplacement() },
        onBack = props.onBack
      ),
    presentationStyle = Root
  )
}

sealed interface DeviceSettingsUiState {
  /**
   * Viewing the metadata screen
   */
  data object ViewingDeviceDataUiState : DeviceSettingsUiState

  /**
   * Initiating hardware recovery once replace device is invoked
   */
  data object InitiatingHardwareRecoveryUiState : DeviceSettingsUiState

  /**
   * Checking in on a pending delay and notify period for lost hardware
   */
  data object HardwareRecoveryDelayAndNotifyUiState : DeviceSettingsUiState

  /**
   * Initiating a hardware sync via nfc tap
   */
  data object TappingForFirmwareMetadataUiState : DeviceSettingsUiState

  /**
   * Initiating a FWUP if an update is available
   */
  data class UpdatingFirmwareUiState(
    val pendingFirmwareUpdate: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : DeviceSettingsUiState
}
