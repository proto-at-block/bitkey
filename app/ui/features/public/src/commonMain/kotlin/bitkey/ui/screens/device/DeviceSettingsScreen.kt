package bitkey.ui.screens.device

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
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
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.collectIsEnabledAsState
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.settings.full.device.AboutDeviceSheetModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.ScanDeviceToWipeSheetBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.statemachine.walletmigration.W3UpgradeUiProps
import build.wallet.statemachine.walletmigration.W3UpgradeUiStateMachine
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
  private val w3UpgradeUiStateMachine: W3UpgradeUiStateMachine,
  private val accountConfigService: AccountConfigService,
  private val w3OnboardingFeatureFlag: W3OnboardingFeatureFlag,
) : ScreenPresenter<DeviceSettingsScreen> {
  @Suppress("CyclomaticComplexMethod")
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
    val isFingerprintResetEnabled by remember {
      fingerprintResetAvailabilityService.isAvailable()
    }.collectAsState(initial = false)

    val recovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    // Observe the active account config's hardware type reactively so the upgrade option
    // hides immediately after a W3 upgrade completes (without needing an app restart).
    val accountConfig by remember { accountConfigService.activeOrDefaultConfig() }.collectAsState()
    val activeHardwareType = (accountConfig as? FullAccountConfig)?.hardwareType

    val isW3OnboardingEnabled by w3OnboardingFeatureFlag.collectIsEnabledAsState()

    return when (val state = uiState) {
      is ViewingDeviceDataUiState -> {
        val availability by remember {
          derivedStateOf { firmwareData.firmwareDeviceInfo?.let(::Present) ?: None }
        }
        val goBack = remember(navigator, screen) {
          {
            screen.originScreen?.let {
              navigator.goTo(it)
            } ?: run {
              navigator.exit()
            }
          }
        }

        val replaceDeviceEnabled by remember(securityAndRecoveryStatus) {
          derivedStateOf { securityAndRecoveryStatus == Available }
        }
        fun goToFwup(firmwareUpdateState: FirmwareData.FirmwareUpdateState.PendingUpdate) {
          navigator.goTo(
            screen = FwupScreen(
              firmwareUpdateData = firmwareUpdateState,
              onExit = { navigator.goTo(screen) }
            )
          )
        }
        fun goToManageFingerprints() {
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
        }

        fun leaveDeviceSettings(action: DeviceSettingsExitAction) {
          val currentState = uiState as? ViewingDeviceDataUiState ?: return
          if (currentState.pendingExitAction != null) return

          if (action == DeviceSettingsExitAction.WipeDevice) {
            uiState =
              currentState.copy(
                showingPromptForFingerprintFwUpdate = false,
                showingManageFingerprintsOptions = false,
                showingAboutSheet = false,
                showingWipeDeviceScanSheet = true
              )
            return
          }

          uiState =
            currentState.copy(
              showingPromptForFingerprintFwUpdate = false,
              showingManageFingerprintsOptions = false,
              showingAboutSheet = false,
              showRealtimeMedia = false,
              pendingExitAction = action
            )
        }

        fun routeAndRestoreIfUnhandled(route: Route) {
          Router.route = route
          if (Router.route != null) {
            uiState = ViewingDeviceDataUiState()
          }
        }

        state.pendingExitAction?.let { action ->
          LaunchedEffect(action) {
            withFrameNanos { }
            when (action) {
              DeviceSettingsExitAction.Back -> goBack()
              is DeviceSettingsExitAction.FirmwareUpdate -> goToFwup(action.firmwareUpdateState)
              DeviceSettingsExitAction.ManageFingerprints -> goToManageFingerprints()
              DeviceSettingsExitAction.ManageReplacement -> {
                routeAndRestoreIfUnhandled(
                  Route.NavigationDeeplink(
                    screen = NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
                  )
                )
              }
              DeviceSettingsExitAction.RecoverHardware -> {
                if (replaceDeviceEnabled) {
                  routeAndRestoreIfUnhandled(Route.InitiateHardwareRecovery)
                } else {
                  alertModel = AppFunctionalityStatusAlertModel(
                    status = appFunctionalityStatus as AppFunctionalityStatus.LimitedFunctionality,
                    onDismiss = { alertModel = null }
                  )
                  uiState = ViewingDeviceDataUiState()
                }
              }
              DeviceSettingsExitAction.SyncMetadata -> uiState = TappingForFirmwareMetadataUiState
              DeviceSettingsExitAction.UpgradeDevice -> uiState = W3UpgradeUiState
              DeviceSettingsExitAction.WipeDevice -> uiState = WipingDeviceState()
            }
          }
        }
        val aboutSheetModel = if (state.showingAboutSheet) {
          createAboutSheetModel(
            availability = availability,
            onDismiss = { uiState = ViewingDeviceDataUiState() },
            onSyncDeviceInfo = { leaveDeviceSettings(DeviceSettingsExitAction.SyncMetadata) }
          )
        } else {
          null
        }

        ViewingDeviceScreenModel(
          recovery = recovery,
          firmwareDeviceAvailability = availability,
          activeHardwareType = activeHardwareType,
          goToNfcMetadata = { leaveDeviceSettings(DeviceSettingsExitAction.SyncMetadata) },
          onExitDeviceSettings = ::leaveDeviceSettings,
          onUpgradeDevice = if (activeHardwareType == HardwareType.W3 || !isW3OnboardingEnabled) {
            null
          } else {
            { leaveDeviceSettings(DeviceSettingsExitAction.UpgradeDevice) }
          },
          replaceDeviceEnabled = replaceDeviceEnabled,
          firmwareData = firmwareData,
          showRealtimeMedia = state.showRealtimeMedia,
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
          onBack = {
            leaveDeviceSettings(DeviceSettingsExitAction.Back)
          }
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
                  is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
                    leaveDeviceSettings(DeviceSettingsExitAction.FirmwareUpdate(fwupState))
                  }
                }
              }
            )
            state.showingManageFingerprintsOptions -> ManageFingerprintsOptionsSheetModel(
              onDismiss = { uiState = ViewingDeviceDataUiState() },
              onEditFingerprints = {
                leaveDeviceSettings(DeviceSettingsExitAction.ManageFingerprints)
              },
              onCannotUnlock = {
                uiState = FingerprintResetUiState
              },
              canEditFingerprints = when (availability) {
                is Present -> (availability as Present).firmwareDeviceInfo.hardwareType() != HardwareType.W3
                None -> true
              },
              fingerprintResetEnabled = isFingerprintResetEnabled
            )
            state.showingAboutSheet -> aboutSheetModel
            state.showingWipeDeviceScanSheet -> SheetModel(
              size = SheetSize.DEFAULT,
              onClosed = {
                uiState = state.copy(showingWipeDeviceScanSheet = false)
              },
              body = ScanDeviceToWipeSheetBodyModel(
                onBack = {
                  uiState = state.copy(showingWipeDeviceScanSheet = false)
                },
                onScanToContinue = {
                  uiState = WipingDeviceState(
                    initialStep = WipingDeviceInitialStep.ScanDevice
                  )
                }
              )
            )
            else -> null
          }
        )
      }

      TappingForFirmwareMetadataUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val tappedDeviceInfo =
                verifyTappedDeviceInfoForMetadataSync(
                  expectedPairedDeviceInfo = firmwareData.firmwareDeviceInfo,
                  expectedHwAuthKey = screen.account.keybox.activeHwKeyBundle.authKey,
                  session = session,
                  commands = commands
                )
              firmwareDeviceInfoDao.setDeviceInfo(
                tappedDeviceInfo
              )
            },
            onSuccess = { uiState = ViewingDeviceDataUiState(showingAboutSheet = true) },
            onCancel = { uiState = ViewingDeviceDataUiState(showingAboutSheet = true) },
            needsAuthentication = false,
            hardwareVerification = NotRequired,
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
            fullAccount = screen.account,
            initialStep = state.initialStep
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

      is W3UpgradeUiState -> {
        w3UpgradeUiStateMachine.model(
          W3UpgradeUiProps(
            account = screen.account,
            onUpgradeComplete = {
              // Navigate back to Money Home after successful W3 upgrade and show
              // the immediate post-upgrade hero moment.
              Router.route = Route.W3UpgradeComplete
            },
            onExit = {
              uiState = ViewingDeviceDataUiState()
            }
          )
        )
      }
    }
  }

  private fun createAboutSheetModel(
    availability: FirmwareDeviceAvailability,
    onDismiss: () -> Unit,
    onSyncDeviceInfo: () -> Unit,
  ) = when (availability) {
    None -> AboutDeviceSheetModel(
      modelName = "Bitkey",
      modelNumber = "-",
      serialNumber = "-",
      currentVersion = "-",
      deviceCharge = "-",
      lastSyncDate = "-",
      emptyState = true,
      onDismiss = onDismiss,
      onSyncDeviceInfo = onSyncDeviceInfo
    )
    is Present -> {
      val firmwareDeviceInfo = availability.firmwareDeviceInfo
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
        onDismiss = onDismiss,
        onSyncDeviceInfo = onSyncDeviceInfo
      )
    }
  }

  @Composable
  private fun ViewingDeviceScreenModel(
    recovery: Recovery,
    firmwareData: FirmwareData?,
    firmwareDeviceAvailability: FirmwareDeviceAvailability,
    activeHardwareType: HardwareType?,
    goToNfcMetadata: () -> Unit,
    onExitDeviceSettings: (DeviceSettingsExitAction) -> Unit,
    replaceDeviceEnabled: Boolean,
    showRealtimeMedia: Boolean,
    onManageFingerprints: () -> Unit,
    onShowAboutSheet: () -> Unit,
    onBack: () -> Unit,
    onUpgradeDevice: (() -> Unit)?,
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
      val deviceBatteryPercentage: Int? = null,
      val lastSyncDate: String = noInfo,
      val modelName: String = noInfo,
      val hardwareType: HardwareType = HardwareType.W1,
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
              deviceBatteryPercentage = firmwareDeviceInfo.batteryChargeForUninitializedModelGauge(),
              lastSyncDate =
                dateTimeFormatter.fullShortDateWithTime(
                  localDateTime =
                    Instant.fromEpochSeconds(firmwareDeviceInfo.timeRetrieved)
                      .toLocalDateTime(timeZoneProvider.current())
                ),
              modelName = "Bitkey",
              hardwareType = firmwareDeviceInfo.hardwareType(),
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
          showRealtimeMedia = showRealtimeMedia,
          modelName = modelData.modelName,
          currentVersion = modelData.currentVersion,
          updateVersion = modelData.updateVersion,
          modelNumber = modelData.modelNumber,
          serialNumber = modelData.serialNumber,
          deviceCharge = modelData.deviceCharge,
          deviceBatteryPercentage = modelData.deviceBatteryPercentage,
          lastSyncDate = modelData.lastSyncDate,
          hardwareType = modelData.hardwareType,
          // Prefer the active account hardware type (authoritative, reactive) over the
          // firmware-derived type (stale on first boot after W3 upgrade). Falls back to
          // firmware type when activeHardwareType is not yet available.
          showFingerprintsRow = (activeHardwareType ?: modelData.hardwareType) != HardwareType.W3,
          replaceDeviceEnabled = replaceDeviceEnabled,
          replacementPending = modelData.replacementPending,
          onUpdateVersion = when (val firmwareUpdateState = firmwareData?.firmwareUpdateState) {
            is FirmwareData.FirmwareUpdateState.UpToDate, null -> null
            is FirmwareData.FirmwareUpdateState.PendingUpdate -> {
              { onExitDeviceSettings(DeviceSettingsExitAction.FirmwareUpdate(firmwareUpdateState)) }
            }
          },
          onSyncDeviceInfo = goToNfcMetadata,
          onReplaceDevice = { onExitDeviceSettings(DeviceSettingsExitAction.RecoverHardware) },
          onManageReplacement = {
            onExitDeviceSettings(DeviceSettingsExitAction.ManageReplacement)
          },
          onWipeDevice = { onExitDeviceSettings(DeviceSettingsExitAction.WipeDevice) },
          onPairDevice = {
            Router.route = Route.NavigationDeeplink(
              screen = NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
            )
          },
          onBack = onBack,
          onShowAboutSheet = onShowAboutSheet,
          onManageFingerprints = onManageFingerprints,
          onUpgradeDevice = onUpgradeDevice
        )
      },
      presentationStyle = Root
    )
  }
}

@Suppress("ThrowsCount")
internal suspend fun verifyTappedDeviceInfoForMetadataSync(
  expectedPairedDeviceInfo: FirmwareDeviceInfo?,
  expectedHwAuthKey: HwAuthPublicKey,
  session: NfcSession,
  commands: NfcCommands,
): FirmwareDeviceInfo {
  val tappedDeviceInfo = commands.getDeviceInfo(session)
  val tappedHardwareType = tappedDeviceInfo.hardwareType()
  val expectedPairedHardwareType = expectedPairedDeviceInfo?.hardwareType()

  if (expectedPairedHardwareType != null && tappedHardwareType != expectedPairedHardwareType) {
    throw NfcException.UnpairedHardwareError()
  }

  when (tappedHardwareType) {
    HardwareType.W3 -> {
      val pairedSerial = expectedPairedDeviceInfo?.serial
      if (pairedSerial == null || tappedDeviceInfo.serial != pairedSerial) {
        throw NfcException.UnpairedHardwareError()
      }
    }
    HardwareType.W1 -> {
      val tappedHwAuthKey = commands.getAuthenticationKey(session)
      if (tappedHwAuthKey != expectedHwAuthKey) {
        throw NfcException.UnpairedHardwareError()
      }
    }
  }

  return tappedDeviceInfo
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
    val showingWipeDeviceScanSheet: Boolean = false,
    val showRealtimeMedia: Boolean = true,
    val pendingExitAction: DeviceSettingsExitAction? = null,
  ) : DeviceSettingsUiState

  /**
   * Initiating a hardware sync via nfc tap
   */
  data object TappingForFirmwareMetadataUiState : DeviceSettingsUiState

  /**
   * Wiping the device
   */
  data class WipingDeviceState(
    val initialStep: WipingDeviceInitialStep = WipingDeviceInitialStep.Intro,
  ) : DeviceSettingsUiState

  /**
   * Showing the fingerprint reset flow
   */
  data object FingerprintResetUiState : DeviceSettingsUiState

  /**
   * Showing the W3 upgrade flow
   */
  data object W3UpgradeUiState : DeviceSettingsUiState
}

private sealed interface DeviceSettingsExitAction {
  data object Back : DeviceSettingsExitAction
  data class FirmwareUpdate(
    val firmwareUpdateState: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : DeviceSettingsExitAction
  data object ManageFingerprints : DeviceSettingsExitAction
  data object ManageReplacement : DeviceSettingsExitAction
  data object RecoverHardware : DeviceSettingsExitAction
  data object SyncMetadata : DeviceSettingsExitAction
  data object UpgradeDevice : DeviceSettingsExitAction
  data object WipeDevice : DeviceSettingsExitAction
}

private sealed interface EnrolledFingerprintResult {
  /** A firmware update is required to support multiple fingerprints. */
  data object FwUpRequired : EnrolledFingerprintResult

  data class Success(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrolledFingerprintResult
}
