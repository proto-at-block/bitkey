package bitkey.ui.screens.device

import androidx.compose.runtime.*
import bitkey.privilegedactions.FingerprintResetAvailabilityService
import bitkey.recovery.RecoveryStatusService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.device.DeviceSettingsUiState.*
import bitkey.ui.screens.device.FirmwareDeviceAvailability.None
import bitkey.ui.screens.device.FirmwareDeviceAvailability.Present
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.AboutDeviceSheetModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
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

/**
 * Device settings props
 */
data class DeviceSettingsScreen(
  val account: FullAccount,
  val originScreen: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class DeviceSettingsScreenPresenter(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val durationFormatter: DurationFormatter,
  private val appFunctionalityService: AppFunctionalityService,
  private val wipingDeviceUiStateMachine: WipingDeviceUiStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val fingerprintResetUiStateMachine: FingerprintResetUiStateMachine,
  private val fingerprintResetAvailabilityService: FingerprintResetAvailabilityService,
  private val recoveryStatusService: RecoveryStatusService,
  private val clock: Clock,
) : ScreenPresenter<DeviceSettingsScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DeviceSettingsScreen,
  ): ScreenModel {
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

    val recovery by remember {
      recoveryStatusService.status
    }.collectAsState()

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
              replaceDeviceEnabled -> Router.route = Route.InitiateHardwareRecovery
              else -> {
                alertModel = AppFunctionalityStatusAlertModel(
                  status = appFunctionalityStatus as AppFunctionalityStatus.LimitedFunctionality,
                  onDismiss = { alertModel = null }
                )
              }
            }
          }
        }
        val aboutSheetModel = if (state.showingAboutSheet) {
          when (availability) {
            None -> AboutDeviceSheetModel(
              modelName = "Bitkey",
              modelNumber = "-",
              serialNumber = "-",
              currentVersion = "-",
              deviceCharge = "-",
              lastSyncDate = "-",
              emptyState = true,
              onDismiss = { uiState = ViewingDeviceDataUiState() },
              onSyncDeviceInfo = { uiState = TappingForFirmwareMetadataUiState }
            )
            is Present -> {
              val present = availability as Present
              val firmwareDeviceInfo = present.firmwareDeviceInfo
              AboutDeviceSheetModel(
                modelName = "Bitkey",
                modelNumber = firmwareDeviceInfo.hwRevision,
                serialNumber = firmwareDeviceInfo.serial,
                currentVersion = firmwareDeviceInfo.mcuInfo.takeIf { it.isNotEmpty() }
                  ?.joinToString("/") { it.firmwareVersion } ?: firmwareDeviceInfo.version,
                deviceCharge = "${firmwareDeviceInfo.batteryChargeForUninitializedModelGauge()}%",
                lastSyncDate = dateTimeFormatter.fullShortDateWithTime(
                  localDateTime = Instant.fromEpochSeconds(firmwareDeviceInfo.timeRetrieved)
                    .toLocalDateTime(timeZoneProvider.current())
                ),
                emptyState = false,
                onDismiss = { uiState = ViewingDeviceDataUiState() },
                onSyncDeviceInfo = { uiState = TappingForFirmwareMetadataUiState }
              )
            }
          }
        } else {
          null
        }

        ViewingDeviceScreenModel(
          screen = screen,
          recovery = recovery,
          firmwareDeviceAvailability = availability,
          goToFwup = {
            navigator.goTo(
              screen = FwupScreen(
                firmwareUpdateData = it,
                onExit = { navigator.goTo(screen) }
              )
            )
          },
          goToNfcMetadata = { uiState = TappingForFirmwareMetadataUiState },
          goToRecovery = goToRecovery,
          onManageReplacement = {
            Router.route = Route.NavigationDeeplink(
              screen = NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
            )
          },
          onWipeDevice = { uiState = WipingDeviceState },
          replaceDeviceEnabled = replaceDeviceEnabled,
          firmwareData = firmwareData,
          onManageFingerprints = {
            uiState = state.copy(
              showingManageFingerprintsOptions = true
            )
          },
          onShowAboutSheet = {
            uiState = state.copy(
              showingAboutSheet = true
            )
          },
          navigator = navigator
        ).copy(
          alertModel = alertModel,
          bottomSheetModel = when {
            state.showingPromptForFingerprintFwUpdate -> PromptingForFingerprintFwUpSheetModel(
              onCancel = { uiState = ViewingDeviceDataUiState() },
              onUpdate = {
                when (val fwupState = firmwareData.firmwareUpdateState) {
                  FirmwareData.FirmwareUpdateState.UpToDate -> {
                    uiState = ViewingDeviceDataUiState()
                  }
                  is FirmwareData.FirmwareUpdateState.PendingUpdate -> navigator.goTo(
                    screen = FwupScreen(
                      firmwareUpdateData = fwupState,
                      onExit = { navigator.goTo(screen) }
                    )
                  )
                }
              }
            )
            state.showingManageFingerprintsOptions -> ManageFingerprintsOptionsSheetModel(
              onDismiss = { uiState = ViewingDeviceDataUiState() },
              onEditFingerprints = {
                navigator.goTo(
                  screen = ManagingFingerprintsScreen(
                    account = screen.account,
                    onFwUpRequired = {
                      uiState = ViewingDeviceDataUiState(
                        showingPromptForFingerprintFwUpdate = true
                      )
                    },
                    entryPoint = EntryPoint.DEVICE_SETTINGS,
                    origin = screen
                  )
                )
              },
              onCannotUnlock = {
                uiState = FingerprintResetUiState
              },
              fingerprintResetEnabled = isFingerprintResetEnabled
            )
            state.showingAboutSheet -> aboutSheetModel
            else -> null
          }
        )
      }

      TappingForFirmwareMetadataUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              firmwareDeviceInfoDao.setDeviceInfo(
                commands.getDeviceInfo(session)
              )
            },
            onSuccess = { uiState = ViewingDeviceDataUiState(showingAboutSheet = true) },
            onCancel = { uiState = ViewingDeviceDataUiState(showingAboutSheet = true) },
            needsAuthentication = false,
            shouldLock = false,
            screenPresentationStyle = Modal,
            eventTrackerContext = METADATA
          )
        )

      is WipingDeviceState -> {
        wipingDeviceUiStateMachine.model(
          props = WipingDeviceProps(
            onBack = { uiState = ViewingDeviceDataUiState() },
            onSuccess = {
              navigator.exit()
            },
            fullAccount = screen.account
          )
        )
      }

      is FingerprintResetUiState -> {
        fingerprintResetUiStateMachine.model(
          props = FingerprintResetProps(
            onComplete = { uiState = ViewingDeviceDataUiState() },
            onCancel = { uiState = ViewingDeviceDataUiState() },
            onFwUpRequired = {
              when (val firmwareUpdateState = firmwareData.firmwareUpdateState) {
                is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
                  navigator.goTo(
                    FwupScreen(
                      firmwareUpdateData = firmwareUpdateState,
                      onExit = {
                        navigator.goTo(screen)
                      }
                    )
                  )
                }
                else -> {
                  uiState = ViewingDeviceDataUiState()
                }
              }
            },
            account = screen.account
          )
        )
      }
    }
  }

  @Composable
  private fun ViewingDeviceScreenModel(
    navigator: Navigator,
    screen: DeviceSettingsScreen,
    recovery: Recovery,
    firmwareData: FirmwareData?,
    firmwareDeviceAvailability: FirmwareDeviceAvailability,
    goToFwup: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
    goToNfcMetadata: () -> Unit,
    goToRecovery: () -> Unit,
    onManageReplacement: () -> Unit,
    onWipeDevice: () -> Unit,
    replaceDeviceEnabled: Boolean,
    onManageFingerprints: () -> Unit,
    onShowAboutSheet: () -> Unit,
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
              replacementPending = when (recovery) {
                is InitiatedRecovery -> {
                  val remainingDelay = nonNegativeDurationBetween(
                    startTime = clock.now(),
                    endTime = recovery.serverRecovery.delayEndTime
                  )
                  durationFormatter.formatWithWords(remainingDelay)
                }
                is StillRecovering -> "Awaiting confirmation"
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
          onPairDevice = {
            Router.route = Route.NavigationDeeplink(
              screen = NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
            )
          },
          onBack = {
            screen.originScreen?.let {
              navigator.goTo(it)
            } ?: run {
              navigator.exit()
            }
          },
          onShowAboutSheet = onShowAboutSheet,
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

private sealed interface DeviceSettingsUiState {
  /**
   * Viewing the metadata screen
   */
  data class ViewingDeviceDataUiState(
    val showingPromptForFingerprintFwUpdate: Boolean = false,
    val showingManageFingerprintsOptions: Boolean = false,
    val showingAboutSheet: Boolean = false,
  ) : DeviceSettingsUiState

  /**
   * Initiating a hardware sync via nfc tap
   */
  data object TappingForFirmwareMetadataUiState : DeviceSettingsUiState

  /**
   * Wiping the device
   */
  data object WipingDeviceState : DeviceSettingsUiState

  /**
   * Showing the fingerprint reset flow
   */
  data object FingerprintResetUiState : DeviceSettingsUiState
}

private sealed interface EnrolledFingerprintResult {
  /** A firmware update is required to support multiple fingerprints. */
  data object FwUpRequired : EnrolledFingerprintResult

  data class Success(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrolledFingerprintResult
}
