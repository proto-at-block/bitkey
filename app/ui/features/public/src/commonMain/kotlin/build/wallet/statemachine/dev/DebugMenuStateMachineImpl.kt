package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.DEBUG
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.METADATA
import build.wallet.balance.utils.DataQuality
import build.wallet.balance.utils.MockConfiguration
import build.wallet.balance.utils.MockPriceScenario
import build.wallet.balance.utils.MockScenarioService
import build.wallet.balance.utils.MockTransactionScenario
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.di.W1
import build.wallet.di.W3
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadataDao
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.fwup.FwupDataDao
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.logging.logFailure
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.NfcException
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.form.formBodyModel
import build.wallet.statemachine.dev.analytics.AnalyticsUiStateMachine
import build.wallet.statemachine.dev.analytics.Props
import build.wallet.statemachine.dev.cloud.CloudDevOptionsProps
import build.wallet.statemachine.dev.cloud.CloudDevOptionsStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigPickerUiStateMachine
import build.wallet.statemachine.dev.debug.NetworkingDebugConfigProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsStateMachine
import build.wallet.statemachine.dev.logs.LogsUiStateMachine
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * State machine with debug menu that allows configuring various options for development
 * and debugging purposes. Handles showing the main list as well as screens off of the
 * list. The list UI is managed by [DebugMenuListStateMachine].
 */
data object DebugMenuScreen : Screen

@BitkeyInject(ActivityScope::class)
class DebugMenuScreenPresenter(
  private val analyticsUiStateMachine: AnalyticsUiStateMachine,
  private val clipboard: Clipboard,
  private val debugMenuListStateMachine: DebugMenuListStateMachine,
  private val f8eCustomUrlStateMachine: F8eCustomUrlStateMachine,
  @W1 private val w1FakeHardwareKeyStore: FakeHardwareKeyStore,
  @W3 private val w3FakeHardwareKeyStore: FakeHardwareKeyStore,
  private val featureFlagsStateMachine: FeatureFlagsStateMachine,
  private val firmwareMetadataUiStateMachine: FirmwareMetadataUiStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val firmwareMetadataDao: FirmwareMetadataDao,
  private val fwupDataDao: FwupDataDao,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val logsUiStateMachine: LogsUiStateMachine,
  private val networkingDebugConfigPickerUiStateMachine: NetworkingDebugConfigPickerUiStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val onboardingCompletionService: OnboardingCompletionService,
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val mockScenarioService: MockScenarioService,
) : ScreenPresenter<DebugMenuScreen> {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(
    navigator: Navigator,
    screen: DebugMenuScreen,
  ): ScreenModel {
    var uiState: DebugMenuState by remember { mutableStateOf(DebugMenuState.ShowingDebugMenu) }

    val firmwareData by remember {
      firmwareDataService.firmwareData()
    }.collectAsState()

    var pasteboardToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect("pasteboard-toast-timeout", pasteboardToast) {
      if (pasteboardToast != null) {
        delay(3.seconds)
        pasteboardToast = null
      }
    }

    // Collapsed groups state - persists across subscreen navigation
    // Default: all sections collapsed for easier navigation
    var collapsedGroups by remember {
      mutableStateOf(
        persistentSetOf(
          "Feature Flags",
          "Onboarding",
          "Debug Options",
          "Logs",
          "F8e Environment",
          "Bitcoin network",
          "Hardware",
          "Identifiers (tap to copy)",
          "Analytics",
          "Data Management",
          "Keybox Configuration"
        )
      )
    }

    return when (val state = uiState) {
      is DebugMenuState.ShowingDebugMenu ->
        debugMenuListStateMachine.model(
          props = DebugMenuListProps(
            navigator = navigator,
            firmwareData = firmwareData,
            onSetState = { uiState = it },
            onClose = { navigator.exit() },
            onPasteboardCopy = { pasteboardToast = it },
            collapsedGroupHeaders = collapsedGroups,
            onToggleGroupCollapse = { header ->
              collapsedGroups = if (collapsedGroups.contains(header)) {
                collapsedGroups.remove(header)
              } else {
                collapsedGroups.add(header)
              }
            }
          )
        ).asModalScreen(
          toastModel = pasteboardToast?.let {
            ToastModel(
              leadingIcon = IconModel(
                icon = Icon.SmallIconCheckStroked,
                iconSize = IconSize.Small,
                iconTint = IconTint.Success
              ),
              title = "Copied $it",
              iconStrokeColor = ToastModel.IconStrokeColor.Unspecified
            )
          }
        )

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

      is DebugMenuState.ShowingAnalytics ->
        analyticsUiStateMachine.model(
          Props(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.ShowingFeatureFlags ->
        featureFlagsStateMachine.model(
          FeatureFlagsProps(onBack = { uiState = DebugMenuState.ShowingDebugMenu })
        )

      is DebugMenuState.ShowingMockDataProvider ->
        MockDataProviderBodyModel(
          mockScenarioService = mockScenarioService,
          onBack = { uiState = DebugMenuState.ShowingDebugMenu },
          onShowSeedInput = { uiState = DebugMenuState.ShowingMockSeedInput },
          onSeedCopied = { seed -> pasteboardToast = "seed $seed" }
        ).asModalScreen()

      is DebugMenuState.ShowingMockSeedInput ->
        MockSeedInputBodyModel(
          mockScenarioService = mockScenarioService,
          onBack = { uiState = DebugMenuState.ShowingMockDataProvider }
        )

      is DebugMenuState.ShowingFakeHardwareSeed -> {
        var w1Seed by remember { mutableStateOf<String?>(null) }
        var w3Seed by remember { mutableStateOf<String?>(null) }
        var w1SeedInput by remember { mutableStateOf("") }
        var w3SeedInput by remember { mutableStateOf("") }
        val coroutineScope = rememberStableCoroutineScope()

        LaunchedEffect(Unit) {
          w1Seed = w1FakeHardwareKeyStore.getSeed().words
          w3Seed = w3FakeHardwareKeyStore.getSeed().words
        }

        DualFakeHardwareSeedFormBodyModel(
          w1CurrentSeed = w1Seed ?: "Loading...",
          w3CurrentSeed = w3Seed ?: "Loading...",
          w1SeedInput = w1SeedInput,
          w3SeedInput = w3SeedInput,
          onW1SeedInputChanged = { w1SeedInput = it },
          onW3SeedInputChanged = { w3SeedInput = it },
          onCopyW1Seed = {
            w1Seed?.let { seed ->
              clipboard.setItem(PlainText(data = seed))
              pasteboardToast = "W1 fake hardware seed"
            }
          },
          onCopyW3Seed = {
            w3Seed?.let { seed ->
              clipboard.setItem(PlainText(data = seed))
              pasteboardToast = "W3 fake hardware seed"
            }
          },
          onApplyW1Seed = {
            coroutineScope.launch {
              if (w1SeedInput.isNotBlank()) {
                w1FakeHardwareKeyStore.setSeed(FakeHardwareKeyStore.Seed(w1SeedInput.trim()))
                w1Seed = w1SeedInput.trim()
                w1SeedInput = ""
              }
            }
          },
          onApplyW3Seed = {
            coroutineScope.launch {
              if (w3SeedInput.isNotBlank()) {
                w3FakeHardwareKeyStore.setSeed(FakeHardwareKeyStore.Seed(w3SeedInput.trim()))
                w3Seed = w3SeedInput.trim()
                w3SeedInput = ""
              }
            }
          },
          isW1ApplyEnabled = w1SeedInput.isNotBlank(),
          isW3ApplyEnabled = w3SeedInput.isNotBlank(),
          onBack = { uiState = DebugMenuState.ShowingDebugMenu }
        ).asModalScreen()
      }

      is DebugMenuState.ShowingCloudStorageDebugOptions ->
        cloudDevOptionsStateMachine.model(
          CloudDevOptionsProps(onExit = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.ShowingNetworkingDebugOptions ->
        networkingDebugConfigPickerUiStateMachine.model(
          NetworkingDebugConfigProps(onExit = { uiState = DebugMenuState.ShowingDebugMenu })
        ).asModalScreen()

      is DebugMenuState.VerifyingFirmwareMetadata ->
        VerifyingFirmwareMetadataScreen(
          onBack = { uiState = DebugMenuState.ShowingDebugMenu },
          onContinue = {
            // After verification, proceed to MCU selection with refreshed firmware data.
            uiState = DebugMenuState.ShowingFirmwareUpdateDetails
          }
        )

      is DebugMenuState.ShowingFirmwareUpdateDetails -> {
        // The firmwareData StateFlow is reactive — it may take a moment to
        // reflect the freshly downloaded fwup data from the verification step.
        // Show a loading state while waiting, with a timeout to detect no update.
        var timedOut by remember { mutableStateOf(false) }
        val pendingUpdate = firmwareData.firmwareUpdateState as? FirmwareData.FirmwareUpdateState.PendingUpdate

        if (pendingUpdate != null) {
          FirmwareUpdateDetailsBodyModel(
            pendingUpdate = pendingUpdate,
            currentDeviceInfo = firmwareData.firmwareDeviceInfo,
            onBack = { uiState = DebugMenuState.ShowingDebugMenu },
            onContinue = { selectedUpdate ->
              uiState = DebugMenuState.UpdatingFirmware(selectedUpdate)
            }
          ).asModalScreen()
        } else if (!timedOut) {
          // Wait for the StateFlow to catch up
          LaunchedEffect("wait-for-fwup-data") {
            delay(5.seconds)
            timedOut = true
          }
          LoadingBodyModel(
            title = "Loading firmware update data...",
            onBack = { uiState = DebugMenuState.ShowingDebugMenu },
            id = null
          ).asModalScreen()
        } else {
          // No firmware update available from memfault
          formBodyModel(
            id = null,
            onBack = { uiState = DebugMenuState.ShowingDebugMenu },
            toolbar = ToolbarModel(
              leadingAccessory = BackAccessory(onClick = {
                uiState = DebugMenuState.ShowingDebugMenu
              })
            ),
            header = FormHeaderModel(
              headline = "No Firmware Update Available",
              subline = "No firmware packages are currently served from Memfault for this device. Check that the device info and hardware revision are correct."
            ),
            primaryButton = ButtonModel(
              text = "Back to Debug Menu",
              size = ButtonModel.Size.Footer,
              onClick = StandardClick { uiState = DebugMenuState.ShowingDebugMenu }
            )
          ).asModalScreen()
        }
      }

      is DebugMenuState.UpdatingFirmware ->
        fwupNfcUiStateMachine.model(
          props =
            FwupNfcUiProps(
              onDone = { uiState = DebugMenuState.ShowingDebugMenu },
              selectedMcuUpdates = state.firmwareData.mcuUpdates,
              hardwareTypeOverride = firmwareData.firmwareDeviceInfo.hardwareTypeForRealDevice(),
              showNativeSheetOnIos = false
            )
        )

      is DebugMenuState.WipingHardware -> {
        val coroutineScope = rememberStableCoroutineScope()
        wipingHardwareModel(
          onSuccess = {
            coroutineScope.launch {
              w1FakeHardwareKeyStore.clear()
              w3FakeHardwareKeyStore.clear()
              uiState = DebugMenuState.ShowingDebugMenu
            }
          },
          onCancel = { uiState = DebugMenuState.ShowingDebugMenu }
        )
      }

      is DebugMenuState.ShowingFirmwareMetadata ->
        firmwareMetadataUiStateMachine.model(
          props =
            FirmwareMetadataUiProps(
              onBack = { uiState = DebugMenuState.ShowingDebugMenu }
            )
        )

      is DebugMenuState.ClearingOnboardingData -> {
        LaunchedEffect(state) {
          when (state) {
            is DebugMenuState.ClearingOnboardingData.OnboardingTimestamp -> {
              onboardingCompletionService.clearOnboardingTimestamp()
              uiState = DebugMenuState.ShowingDebugMenu
            }
            is DebugMenuState.ClearingOnboardingData.HasSeenUpsell -> {
              inheritanceUpsellService.reset()
              uiState = DebugMenuState.ShowingDebugMenu
            }
          }
        }

        LoadingBodyModel(
          title = when (state) {
            is DebugMenuState.ClearingOnboardingData.OnboardingTimestamp -> "Clearing onboarding timestamp..."
            is DebugMenuState.ClearingOnboardingData.HasSeenUpsell -> "Clearing has seen upsell state..."
          },
          onBack = { uiState = DebugMenuState.ShowingDebugMenu },
          id = null
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun wipingHardwareModel(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands -> commands.wipeDevice(session) },
        hardwareVerification = NotRequired,
        onSuccess = { success: Boolean ->
          if (!success) {
            throw NfcException.UnknownError(message = "Failed to wipe device")
          }
          onSuccess()
        },
        onCancel = onCancel,
        screenPresentationStyle = Modal,
        eventTrackerContext = DEBUG,
        shouldLock = false,
        confirmationContent = HardwareConfirmationContent.WipeDevice,
        confirmationResultContent = ConfirmationResultContent(
          pendingHeadline = "Confirm the wipe on your Bitkey",
          pendingSubline = "You'll need to approve or deny the wipe on your Bitkey device before tapping again."
        ),
        showNativeSheetOnIos = false
      )
    )
  }

  /**
   * Screen that forces a fresh memfault download and NFC metadata read before
   * proceeding to the MCU selection screen. Helps verify correct slot direction
   * (e.g. b→a for UXC vs a→b).
   */
  @Composable
  private fun VerifyingFirmwareMetadataScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
  ): ScreenModel {
    // Sub-states for this verification flow:
    // 1. Clear stale fwup data (so the StateFlow emits UpToDate before we proceed)
    // 2. NFC tap to read fresh device info + active slots
    // 3. Save to DB, clear fwup cache, download fresh from memfault
    // 4. Proceed to MCU selection (reads reactively from firmwareData StateFlow)
    var verifyState: VerifyMetadataSubState by remember {
      mutableStateOf(VerifyMetadataSubState.ClearingStaleData)
    }

    return when (val current = verifyState) {
      is VerifyMetadataSubState.ClearingStaleData -> {
        // Clear stale fwup data up front so the firmwareData StateFlow drops any
        // previous PendingUpdate. This guarantees that when we reach the MCU
        // selection screen, any PendingUpdate is from the fresh memfault download.
        LaunchedEffect("clear-stale") {
          fwupDataDao.clearAllMcuFwupData()
            .logFailure { "Failed to clear stale MCU fwup data" }
          fwupDataDao.clear()
            .logFailure { "Failed to clear stale fwup data" }
          verifyState = VerifyMetadataSubState.ReadingNfcMetadata
        }
        LoadingBodyModel(
          title = "Preparing...",
          onBack = onBack,
          id = null
        ).asModalScreen()
      }

      is VerifyMetadataSubState.ReadingNfcMetadata -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val deviceInfo = commands.getDeviceInfo(session)
              val enrichedMcuInfo = deviceInfo.mcuInfo.map { mcu ->
                if (mcu.activeSlot != null) {
                  mcu
                } else {
                  @Suppress("TooGenericExceptionCaught", "SwallowedException")
                  try {
                    val metadata = commands.getFirmwareMetadata(session, mcu.mcuRole)
                    mcu.copy(activeSlot = metadata.activeSlot)
                  } catch (e: Exception) {
                    mcu
                  }
                }
              }
              val enrichedDeviceInfo = deviceInfo.copy(mcuInfo = enrichedMcuInfo)
              val firmwareMetadata = commands.getFirmwareMetadata(session)
              Pair(enrichedDeviceInfo, firmwareMetadata)
            },
            onSuccess = { (deviceInfo, metadata) ->
              verifyState = VerifyMetadataSubState.SavingAndSyncing(deviceInfo, metadata)
            },
            onCancel = { onBack() },
            needsAuthentication = false,
            skipFirmwareTelemetry = true,
            screenPresentationStyle = Modal,
            eventTrackerContext = METADATA,
            showNativeSheetOnIos = false
          )
        )
      }

      is VerifyMetadataSubState.SavingAndSyncing -> {
        LaunchedEffect("save-and-sync") {
          // Save fresh device info to DB first so memfault sync uses correct active slots
          firmwareDeviceInfoDao.setDeviceInfo(current.deviceInfo)
          firmwareMetadataDao.setFirmwareMetadata(current.metadata)

          // Sync from memfault — uses the fresh device info we just saved
          // (stale fwup data was already cleared in ClearingStaleData)
          firmwareDataService.syncLatestFwupData()
            .logFailure { "Failed to sync firmware data from memfault" }

          // Go straight to MCU selection — it reactively reads the StateFlow
          onContinue()
        }
        LoadingBodyModel(
          title = "Downloading latest firmware from Memfault...",
          onBack = null,
          id = null
        ).asModalScreen()
      }
    }
  }

  private sealed class VerifyMetadataSubState {
    data object ClearingStaleData : VerifyMetadataSubState()

    data object ReadingNfcMetadata : VerifyMetadataSubState()

    data class SavingAndSyncing(
      val deviceInfo: FirmwareDeviceInfo,
      val metadata: FirmwareMetadata,
    ) : VerifyMetadataSubState()
  }
}

sealed interface DebugMenuState {
  data object ShowingDebugMenu : DebugMenuState

  data class ShowingF8eCustomUrl(
    val customUrl: String,
  ) : DebugMenuState

  data object ShowingLogs : DebugMenuState

  data object ShowingNetworkingDebugOptions : DebugMenuState

  data object ShowingCloudStorageDebugOptions : DebugMenuState

  data object ShowingAnalytics : DebugMenuState

  data object ShowingFeatureFlags : DebugMenuState

  data object ShowingMockDataProvider : DebugMenuState

  data object ShowingMockSeedInput : DebugMenuState

  data object ShowingFakeHardwareSeed : DebugMenuState

  /**
   * Shows firmware update details before starting the update.
   * Reads the latest firmware data from the service (populated by the verification step).
   */
  data object ShowingFirmwareUpdateDetails : DebugMenuState

  /**
   * Intermediate state that forces a fresh memfault download, reads firmware metadata
   * via NFC, and shows it before proceeding with the update. This helps verify the
   * correct slot direction (e.g. b→a for UXC vs a→b).
   */
  data object VerifyingFirmwareMetadata : DebugMenuState

  data class UpdatingFirmware(
    val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : DebugMenuState

  data object WipingHardware : DebugMenuState

  data object ShowingFirmwareMetadata : DebugMenuState

  sealed interface ClearingOnboardingData : DebugMenuState {
    data object OnboardingTimestamp : ClearingOnboardingData

    data object HasSeenUpsell : ClearingOnboardingData
  }
}

@Composable
private fun MockDataProviderBodyModel(
  mockScenarioService: MockScenarioService,
  onBack: () -> Unit,
  onShowSeedInput: () -> Unit,
  onSeedCopied: (String) -> Unit,
): DebugMenuBodyModel {
  var refreshTrigger by remember { mutableStateOf(0) }

  val onConfigurationChanged: () -> Unit = {
    refreshTrigger++
  }

  return DebugMenuBodyModel(
    title = "Mock Data Provider",
    onBack = onBack,
    groups = immutableListOfNotNull(
      ProvideMockPriceScenariosGroup(mockScenarioService, onConfigurationChanged, refreshTrigger),
      ProvideMockDataQualityGroup(mockScenarioService, onConfigurationChanged, refreshTrigger),
      ProvideMockTransactionScenariosGroup(mockScenarioService, onConfigurationChanged, refreshTrigger),
      ProvideMockChartDataControlsGroup(mockScenarioService, onShowSeedInput, onSeedCopied, refreshTrigger)
    ),
    alertModel = null
  )
}

@Composable
private fun MockSeedInputBodyModel(
  mockScenarioService: MockScenarioService,
  onBack: () -> Unit,
): ScreenModel {
  val coroutineScope = rememberCoroutineScope()
  var currentConfig by remember { mutableStateOf<MockConfiguration?>(null) }
  var seedInput by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    currentConfig = mockScenarioService.currentMockConfiguration()
    seedInput = currentConfig?.seed?.toString() ?: ""
  }

  return formBodyModel(
    id = null,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Seed Options",
      subline = currentConfig?.let { config ->
        "Current: ${config.priceScenario?.displayName} + ${config.transactionScenario?.displayName}"
      }
    ),
    mainContentList = immutableListOf(
      TextInput(
        title = "Custom Seed Value",
        fieldModel = TextFieldModel(
          value = seedInput,
          placeholderText = "Enter numeric seed (e.g., 12345)",
          onValueChange = { newValue, _ -> seedInput = newValue },
          keyboardType = TextFieldModel.KeyboardType.Number,
          testTag = "debug-mock-seed-custom-input"
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Apply Custom Seed",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        coroutineScope.launch {
          try {
            val seed = seedInput.toLong()
            val config = currentConfig
            if (config != null) {
              val newConfig = config.copy(seed = seed, generatedAt = Clock.System.now())
              mockScenarioService.setConfiguration(newConfig)
            } else {
              // Default to sideways market and casual user if no config
              val newConfig = MockConfiguration(
                priceScenario = MockPriceScenario.SIDEWAYS_MARKET,
                transactionScenario = MockTransactionScenario.CASUAL_USER,
                dataQuality = DataQuality.Perfect,
                seed = seed,
                generatedAt = Clock.System.now()
              )
              mockScenarioService.setConfiguration(newConfig)
            }
            onBack()
          } catch (e: NumberFormatException) {
            // NOOP
          }
        }
      }
    ),
    secondaryButton = ButtonModel(
      text = "Rotate Seed",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        coroutineScope.launch {
          mockScenarioService.rotateSeed()
          onBack()
        }
      }
    )
  ).asModalScreen()
}

/**
 * Body model for the Fake Hardware Seed screen in the debug menu.
 * Allows viewing/copying the current seed and importing a seed from another device.
 */
private data class DualFakeHardwareSeedFormBodyModel(
  val w1CurrentSeed: String,
  val w3CurrentSeed: String,
  val w1SeedInput: String,
  val w3SeedInput: String,
  val onW1SeedInputChanged: (String) -> Unit,
  val onW3SeedInputChanged: (String) -> Unit,
  val onCopyW1Seed: () -> Unit,
  val onCopyW3Seed: () -> Unit,
  val onApplyW1Seed: () -> Unit,
  val onApplyW3Seed: () -> Unit,
  val isW1ApplyEnabled: Boolean,
  val isW3ApplyEnabled: Boolean,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = DebugMenuEventTrackerScreenId.FAKE_HARDWARE_SEED,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Fake Hardware Seeds",
      subline = "W1 and W3 fake hardware have independent seeds. " +
        "Share seeds with another device to use the same mock hardware."
    ),
    mainContentList = immutableListOf(
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = "W1 Seed",
            sideText = "",
            explainer = DataList.Data.Explainer(
              title = w1CurrentSeed,
              subtitle = ""
            ),
            onClick = onCopyW1Seed
          ),
          DataList.Data(
            title = "W3 Seed",
            sideText = "",
            explainer = DataList.Data.Explainer(
              title = w3CurrentSeed,
              subtitle = ""
            ),
            onClick = onCopyW3Seed
          )
        )
      ),
      TextInput(
        title = "Import W1 Seed",
        fieldModel = TextFieldModel(
          value = w1SeedInput,
          placeholderText = "Paste W1 24-word seed phrase",
          onValueChange = { newValue, _ -> onW1SeedInputChanged(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default,
          testTag = "debug-fake-hardware-w1-seed-import-input"
        )
      ),
      TextInput(
        title = "Import W3 Seed",
        fieldModel = TextFieldModel(
          value = w3SeedInput,
          placeholderText = "Paste W3 24-word seed phrase",
          onValueChange = { newValue, _ -> onW3SeedInputChanged(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default,
          testTag = "debug-fake-hardware-w3-seed-import-input"
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Apply W1 Seed",
      size = ButtonModel.Size.Footer,
      isEnabled = isW1ApplyEnabled,
      onClick = StandardClick { onApplyW1Seed() }
    ),
    secondaryButton = ButtonModel(
      text = "Apply W3 Seed",
      size = ButtonModel.Size.Footer,
      isEnabled = isW3ApplyEnabled,
      onClick = StandardClick { onApplyW3Seed() }
    )
  )

/**
 * Body model for the firmware update details screen shown before starting FWUP.
 * Displays current device version and target update version for each MCU,
 * with checkboxes to select which MCUs to include in the update (all checked by default).
 */
@Composable
private fun FirmwareUpdateDetailsBodyModel(
  pendingUpdate: FirmwareData.FirmwareUpdateState.PendingUpdate,
  currentDeviceInfo: FirmwareDeviceInfo?,
  onBack: () -> Unit,
  onContinue: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
): FormBodyModel {
  val mcuUpdates = pendingUpdate.mcuUpdates

  // Track which MCU indices are selected (all checked by default)
  var selectedIndices by remember {
    mutableStateOf(mcuUpdates.indices.toSet())
  }

  // Build list items for each MCU update with check accessories
  val mcuListItems = mcuUpdates.mapIndexed { index, mcuUpdate ->
    val mcuInfo = currentDeviceInfo?.mcuInfo?.find { it.mcuRole == mcuUpdate.mcuRole }
    val currentVersion = mcuInfo?.firmwareVersion
      ?: currentDeviceInfo?.version
      ?: "Unknown"
    val slotPath = mcuInfo?.activeSlot?.let { activeSlot ->
      val targetSlot = if (activeSlot.name == "A") "B" else "A"
      "Slot $activeSlot → $targetSlot"
    } ?: "Slot unknown"

    ListItemModel(
      title = "${mcuUpdate.mcuRole.name} (${mcuUpdate.mcuName.name}) [$slotPath]",
      secondaryText = "$currentVersion → ${mcuUpdate.version} | ${mcuUpdate.fwupMode.name} | ${formatBytes(mcuUpdate.firmware.size)}",
      trailingAccessory = ListItemAccessory.CheckAccessory(
        isChecked = index in selectedIndices
      ),
      onClick = {
        selectedIndices = if (index in selectedIndices) {
          selectedIndices - index
        } else {
          selectedIndices + index
        }
      }
    )
  }

  // Add device info if available
  val deviceInfoItems = currentDeviceInfo?.let { info ->
    listOf(
      DataList.Data(
        title = "Hardware Revision",
        sideText = info.hwRevision
      ),
      DataList.Data(
        title = "Serial",
        sideText = info.serial
      )
    )
  } ?: emptyList()

  val hasSelection = selectedIndices.isNotEmpty()

  return formBodyModel(
    id = null,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Firmware Update Details",
      subline = "Select the MCUs to update.\n${selectedIndices.size} of ${mcuUpdates.size} selected"
    ),
    mainContentList = if (deviceInfoItems.isNotEmpty()) {
      immutableListOf(
        DataList(
          items = deviceInfoItems.toImmutableList()
        ),
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            header = "MCU Updates",
            items = mcuListItems.toImmutableList(),
            style = ListGroupStyle.DIVIDER
          )
        )
      )
    } else {
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            header = "MCU Updates",
            items = mcuListItems.toImmutableList(),
            style = ListGroupStyle.DIVIDER
          )
        )
      )
    },
    primaryButton = ButtonModel(
      text = "Continue with Update",
      isEnabled = hasSelection,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        val selectedMcuUpdates = mcuUpdates.filterIndexed { index, _ -> index in selectedIndices }
        onContinue(
          FirmwareData.FirmwareUpdateState.PendingUpdate(
            mcuUpdates = selectedMcuUpdates.toImmutableList()
          )
        )
      }
    )
  )
}

private fun formatBytes(bytes: Int): String {
  return when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> {
      val mb = (bytes / (1024.0 * 1024.0) * 10).toInt() / 10.0
      "$mb MB"
    }
  }
}

/**
 * Returns hardware type for real devices only.
 * For fake hardware (hwRevision doesn't start with w1/w3), returns null
 * so the account config's hardware type (user's toggle selection) is used.
 */
private fun FirmwareDeviceInfo?.hardwareTypeForRealDevice(): HardwareType? {
  val revision = this?.hwRevision ?: return null
  val isRealHardware = revision.startsWith("w1", ignoreCase = true) ||
    revision.startsWith("w3", ignoreCase = true)
  return if (isRealHardware) this.hardwareType() else null
}
