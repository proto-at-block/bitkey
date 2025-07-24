package build.wallet.statemachine.settings.full.device

import androidx.compose.runtime.*
import bitkey.privilegedactions.FingerprintResetAvailabilityService
import bitkey.ui.framework.NavigatorPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiState.*
import build.wallet.statemachine.settings.full.device.FirmwareDeviceAvailability.None
import build.wallet.statemachine.settings.full.device.FirmwareDeviceAvailability.Present
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.DurationFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.ui.model.alert.ButtonAlertModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class DeviceSettingsUiStateMachineImpl(
  private val lostHardwareRecoveryUiStateMachine: LostHardwareRecoveryUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val fingerprintResetUiStateMachine: FingerprintResetUiStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val durationFormatter: DurationFormatter,
  private val appFunctionalityService: AppFunctionalityService,
  private val wipingDeviceUiStateMachine: WipingDeviceUiStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val clock: Clock,
  private val navigatorPresenter: NavigatorPresenter,
  private val fingerprintResetAvailabilityService: FingerprintResetAvailabilityService,
) : DeviceSettingsUiStateMachine {
  @Composable
  override fun model(props: DeviceSettingsProps): ScreenModel {
    var uiState: DeviceSettingsUiState by remember {
      mutableStateOf(ViewingDeviceDataUiState())
    }

    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()

    val securityAndRecoveryStatus by remember {
      derivedStateOf {
        appFunctionalityStatus.featureStates.securityAndRecovery
      }
    }

    val firmwareData = remember {
      firmwareDataService.firmwareData()
    }.collectAsState().value

    val isFingerprintResetEnabled by fingerprintResetAvailabilityService
      .isAvailable()
      .collectAsState(initial = false)

    return when (val state = uiState) {
      is ViewingDeviceDataUiState -> {
        val availability by remember {
          derivedStateOf { firmwareData.firmwareDeviceInfo?.let(::Present) ?: None }
        }

        val replaceDeviceEnabled by remember(securityAndRecoveryStatus) {
          derivedStateOf { securityAndRecoveryStatus == Available }
        }
        val goToRecovery = remember(replaceDeviceEnabled, appFunctionalityStatus) {
          {
            when {
              replaceDeviceEnabled -> uiState = InitiatingHardwareRecoveryUiState
              else -> {
                alertModel = AppFunctionalityStatusAlertModel(
                  status = appFunctionalityStatus as AppFunctionalityStatus.LimitedFunctionality,
                  onDismiss = { alertModel = null }
                )
              }
            }
          }
        }

        ViewingDeviceScreenModel(
          props = props,
          firmwareDeviceAvailability = availability,
          goToFwup = { uiState = UpdatingFirmwareUiState(it) },
          goToNfcMetadata = { uiState = TappingForFirmwareMetadataUiState },
          goToRecovery = goToRecovery,
          onManageReplacement = { uiState = HardwareRecoveryDelayAndNotifyUiState },
          onWipeDevice = { uiState = WipingDeviceState },
          replaceDeviceEnabled = replaceDeviceEnabled,
          firmwareData = firmwareData,
          onManageFingerprints = {
            uiState = ViewingDeviceDataUiState(
              showingManageFingerprintsOptions = true
            )
          }
        ).copy(
          alertModel = alertModel,
          bottomSheetModel = when {
            state.showingPromptForFingerprintFwUpdate -> PromptingForFingerprintFwUpSheetModel(
              onCancel = { uiState = ViewingDeviceDataUiState() },
              onUpdate = {
                uiState = when (val fwupState = firmwareData.firmwareUpdateState) {
                  is FirmwareData.FirmwareUpdateState.PendingUpdate -> UpdatingFirmwareUiState(
                    pendingFirmwareUpdate = fwupState
                  )
                  FirmwareData.FirmwareUpdateState.UpToDate -> {
                    ViewingDeviceDataUiState()
                  }
                }
              }
            )
            state.showingManageFingerprintsOptions -> ManageFingerprintsOptionsSheetModel(
              onDismiss = { uiState = ViewingDeviceDataUiState() },
              onEditFingerprints = {
                uiState = ManagingFingerprintsUiState
              },
              onCannotUnlock = {
                uiState = FingerprintResetUiState
              },
              fingerprintResetEnabled = isFingerprintResetEnabled
            )
            else -> null
          } as? SheetModel
        )
      }

      InitiatingHardwareRecoveryUiState ->
        lostHardwareRecoveryUiStateMachine.model(
          props = LostHardwareRecoveryProps(
            account = props.account,
            lostHardwareRecoveryData = props.lostHardwareRecoveryData,
            screenPresentationStyle = Modal,
            instructionsStyle = InstructionsStyle.Independent,
            onFoundHardware = {}, // noop
            onExit = { uiState = ViewingDeviceDataUiState() },
            onComplete = props.onUnwindToMoneyHome
          )
        )

      HardwareRecoveryDelayAndNotifyUiState ->
        lostHardwareRecoveryUiStateMachine.model(
          props = LostHardwareRecoveryProps(
            account = props.account,
            lostHardwareRecoveryData = props.lostHardwareRecoveryData,
            screenPresentationStyle = Modal,
            instructionsStyle = InstructionsStyle.Independent,
            onFoundHardware = {}, // noop
            onExit = { uiState = ViewingDeviceDataUiState() },
            onComplete = props.onUnwindToMoneyHome
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
            onSuccess = { uiState = ViewingDeviceDataUiState() },
            onCancel = { uiState = ViewingDeviceDataUiState() },
            needsAuthentication = false,
            screenPresentationStyle = Modal,
            eventTrackerContext = METADATA
          )
        )

      is UpdatingFirmwareUiState ->
        navigatorPresenter.model(
          initialScreen = FwupScreen(
            firmwareUpdateData = state.pendingFirmwareUpdate,
            onExit = { uiState = ViewingDeviceDataUiState() }
          ),
          onExit = { uiState = ViewingDeviceDataUiState() }
        )

      is ManagingFingerprintsUiState -> navigatorPresenter.model(
        initialScreen = ManagingFingerprintsScreen(
          account = props.account,
          onFwUpRequired = {
            uiState = ViewingDeviceDataUiState(showingPromptForFingerprintFwUpdate = true)
          },
          entryPoint = EntryPoint.DEVICE_SETTINGS,
          origin = null
        ),
        onExit = {
          uiState = ViewingDeviceDataUiState()
        }
      )

      is WipingDeviceState -> {
        wipingDeviceUiStateMachine.model(
          props = WipingDeviceProps(
            onBack = { uiState = ViewingDeviceDataUiState() },
            onSuccess = props.onUnwindToMoneyHome,
            fullAccount = props.account
          )
        )
      }

      is FingerprintResetUiState -> {
        fingerprintResetUiStateMachine.model(
          props = FingerprintResetProps(
            onComplete = { _ -> uiState = ViewingDeviceDataUiState() },
            onCancel = { uiState = ViewingDeviceDataUiState() },
            onFwUpRequired = {
              uiState = when (val firmwareUpdateState = firmwareData?.firmwareUpdateState) {
                is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
                  UpdatingFirmwareUiState(firmwareUpdateState)
                }
                else -> {
                  ViewingDeviceDataUiState()
                }
              }
            },
            account = props.account
          )
        )
      }
    }
  }

  @Composable
  private fun ViewingDeviceScreenModel(
    props: DeviceSettingsProps,
    firmwareData: FirmwareData?,
    firmwareDeviceAvailability: FirmwareDeviceAvailability,
    goToFwup: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
    goToNfcMetadata: () -> Unit,
    goToRecovery: () -> Unit,
    onManageReplacement: () -> Unit,
    onWipeDevice: () -> Unit,
    replaceDeviceEnabled: Boolean,
    onManageFingerprints: () -> Unit,
  ): ScreenModel {
    val noInfo = "-"

    data class ModelData(
      val trackerScreenId: EventTrackerScreenId,
      val emptyState: Boolean = true,
      val currentVersion: String = noInfo,
      val updateVersion: String? = null,
      val modelNumber: String = noInfo,
      val serialNumber: String = noInfo,
      val deviceCharge: String = noInfo,
      val lastSyncDate: String = noInfo,
      val modelName: String = noInfo,
      val replacementPending: String? = null,
    )
    return ScreenModel(
      body = run {
        val modelData = when (firmwareDeviceAvailability) {
          None -> ModelData(trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO_EMPTY)
          is Present -> {
            val firmwareDeviceInfo by remember {
              derivedStateOf { firmwareDeviceAvailability.firmwareDeviceInfo }
            }
            ModelData(
              trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
              currentVersion = firmwareDeviceInfo.version,
              updateVersion = firmwareData?.updateVersion,
              modelNumber = firmwareDeviceInfo.hwRevision,
              serialNumber = firmwareDeviceInfo.serial,
              deviceCharge = "${firmwareDeviceInfo.batteryChargeForUninitializedModelGauge()}%",
              lastSyncDate =
                dateTimeFormatter.fullShortDateWithTime(
                  localDateTime =
                    Instant.fromEpochSeconds(firmwareDeviceInfo.timeRetrieved)
                      .toLocalDateTime(timeZoneProvider.current())
                ),
              modelName = "Bitkey",
              emptyState = false,
              replacementPending =
                when (val recoveryData = props.lostHardwareRecoveryData) {
                  is LostHardwareRecoveryInProgressData ->
                    when (val recoveryInProgressData = recoveryData.recoveryInProgressData) {
                      is WaitingForRecoveryDelayPeriodData ->
                        durationFormatter.formatWithWords(
                          nonNegativeDurationBetween(
                            startTime = clock.now(),
                            endTime = recoveryInProgressData.delayPeriodEndTime
                          )
                        )
                      is CompletingRecoveryData -> "Awaiting confirmation"
                      else -> null
                    }
                  else -> null
                }
            )
          }
        }
        DeviceSettingsFormBodyModel(
          trackerScreenId = modelData.trackerScreenId,
          emptyState = modelData.emptyState,
          modelName = modelData.modelName,
          currentVersion = modelData.currentVersion,
          updateVersion = modelData.updateVersion,
          modelNumber = modelData.modelNumber,
          serialNumber = modelData.serialNumber,
          deviceCharge = modelData.deviceCharge,
          lastSyncDate = modelData.lastSyncDate,
          replaceDeviceEnabled = replaceDeviceEnabled,
          replacementPending = modelData.replacementPending,
          onUpdateVersion = when (val firmwareUpdateState = firmwareData?.firmwareUpdateState) {
            is FirmwareData.FirmwareUpdateState.UpToDate, null -> null
            is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
              { goToFwup(firmwareUpdateState) }
            }
          },
          onSyncDeviceInfo = goToNfcMetadata,
          onReplaceDevice = goToRecovery,
          onManageReplacement = onManageReplacement,
          onWipeDevice = onWipeDevice,
          onBack = props.onBack,
          onManageFingerprints = onManageFingerprints
        )
      },
      presentationStyle = Root
    )
  }
}

private sealed interface FirmwareDeviceAvailability {
  /**
   * When [FirmwareDeviceInfo] is available
   */
  data class Present(val firmwareDeviceInfo: FirmwareDeviceInfo) : FirmwareDeviceAvailability

  /**
   * When FirmwareDeviceInfo is not available. Can happen in cases when the app doesn't have
   * a device paired
   */
  data object None : FirmwareDeviceAvailability
}

sealed interface DeviceSettingsUiState {
  /**
   * Viewing the metadata screen
   */
  data class ViewingDeviceDataUiState(
    val showingPromptForFingerprintFwUpdate: Boolean = false,
    val showingManageFingerprintsOptions: Boolean = false,
  ) : DeviceSettingsUiState

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

  /**
   * Managing (i.e. adding/editing/deleting) enrolled fingerprints
   */
  data object ManagingFingerprintsUiState : DeviceSettingsUiState

  /**
   * Showing the fingerprint reset flow
   */
  data object FingerprintResetUiState : DeviceSettingsUiState

  /**
   * Wiping the device
   */
  data object WipingDeviceState : DeviceSettingsUiState
}

sealed interface EnrolledFingerprintResult {
  /** A firmware update is required to support multiple fingerprints. */
  data object FwUpRequired : EnrolledFingerprintResult

  data class Success(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrolledFingerprintResult
}
