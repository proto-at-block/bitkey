package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.DEBUG
import build.wallet.balance.utils.DataQuality
import build.wallet.balance.utils.MockConfiguration
import build.wallet.balance.utils.MockPriceScenario
import build.wallet.balance.utils.MockScenarioService
import build.wallet.balance.utils.MockTransactionScenario
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.NfcException
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
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
import build.wallet.statemachine.nfc.ConfirmationHandlerOverride
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
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
  private val fakeHardwareKeyStore: FakeHardwareKeyStore,
  private val featureFlagsStateMachine: FeatureFlagsStateMachine,
  private val firmwareMetadataUiStateMachine: FirmwareMetadataUiStateMachine,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val logsUiStateMachine: LogsUiStateMachine,
  private val networkingDebugConfigPickerUiStateMachine: NetworkingDebugConfigPickerUiStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val cloudDevOptionsStateMachine: CloudDevOptionsStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val onboardingCompletionService: OnboardingCompletionService,
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val mockScenarioService: MockScenarioService,
) : ScreenPresenter<DebugMenuScreen> {
  @Composable
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
        var currentSeed by remember { mutableStateOf<String?>(null) }
        var seedInput by remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
          currentSeed = fakeHardwareKeyStore.getSeed().words
        }

        FakeHardwareSeedFormBodyModel(
          currentSeed = currentSeed ?: "Loading...",
          seedInput = seedInput,
          onSeedInputChanged = { seedInput = it },
          onCopyCurrentSeed = {
            currentSeed?.let { seed ->
              clipboard.setItem(PlainText(data = seed))
              pasteboardToast = "fake hardware seed"
            }
          },
          onApplyImportedSeed = {
            coroutineScope.launch {
              if (seedInput.isNotBlank()) {
                fakeHardwareKeyStore.setSeed(FakeHardwareKeyStore.Seed(seedInput.trim()))
                currentSeed = seedInput.trim()
                seedInput = ""
              }
            }
          },
          isApplyEnabled = seedInput.isNotBlank(),
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

      is DebugMenuState.ShowingFirmwareUpdateDetails ->
        FirmwareUpdateDetailsBodyModel(
          pendingUpdate = state.firmwareData,
          currentDeviceInfo = firmwareData.firmwareDeviceInfo,
          onBack = { uiState = DebugMenuState.ShowingDebugMenu },
          onContinue = { uiState = DebugMenuState.UpdatingFirmware(state.firmwareData) }
        ).asModalScreen()

      is DebugMenuState.UpdatingFirmware ->
        fwupNfcUiStateMachine.model(
          props =
            FwupNfcUiProps(
              onDone = { uiState = DebugMenuState.ShowingDebugMenu }
            )
        )

      is DebugMenuState.WipingHardware -> {
        nfcConfirmableSessionUiStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands -> commands.wipeDevice(session) },
            hardwareVerification = NotRequired,
            onSuccess = { success: Boolean ->
              if (!success) {
                throw NfcException.UnknownError(message = "Failed to wipe device")
              }
              uiState = DebugMenuState.ShowingDebugMenu
            },
            onCancel = { uiState = DebugMenuState.ShowingDebugMenu },
            screenPresentationStyle = Modal,
            eventTrackerContext = DEBUG,
            shouldLock = false,
            onRequiresConfirmation = { _ ->
              ConfirmationHandlerOverride.CompleteImmediately(true)
            },
            onEmulatedPromptSelected = { option ->
              when (option.name) {
                EmulatedPromptOption.APPROVE -> ConfirmationHandlerOverride.CompleteImmediately(true)
                else -> ConfirmationHandlerOverride.CompleteImmediately(false)
              }
            }
          )
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
   */
  data class ShowingFirmwareUpdateDetails(
    val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : DebugMenuState

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
          keyboardType = TextFieldModel.KeyboardType.Number
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
private data class FakeHardwareSeedFormBodyModel(
  val currentSeed: String,
  val seedInput: String,
  val onSeedInputChanged: (String) -> Unit,
  val onCopyCurrentSeed: () -> Unit,
  val onApplyImportedSeed: () -> Unit,
  val isApplyEnabled: Boolean,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = DebugMenuEventTrackerScreenId.FAKE_HARDWARE_SEED,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Fake Hardware Seed",
      subline = "Share this seed with another device to use the same mock hardware. " +
        "This allows cloud backups created on one device to be restored on another."
    ),
    mainContentList = immutableListOf(
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = "Current Seed",
            sideText = "",
            explainer = DataList.Data.Explainer(
              title = currentSeed,
              subtitle = ""
            )
          )
        )
      ),
      TextInput(
        title = "Import Seed from Another Device",
        fieldModel = TextFieldModel(
          value = seedInput,
          placeholderText = "Paste 24-word seed phrase here",
          onValueChange = { newValue, _ -> onSeedInputChanged(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Copy Current Seed",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onCopyCurrentSeed() }
    ),
    secondaryButton = ButtonModel(
      text = "Apply Imported Seed",
      size = ButtonModel.Size.Footer,
      isEnabled = isApplyEnabled,
      onClick = StandardClick { onApplyImportedSeed() }
    )
  )

/**
 * Body model for the firmware update details screen shown before starting FWUP.
 * Displays current device version and target update version for each MCU.
 */
private fun FirmwareUpdateDetailsBodyModel(
  pendingUpdate: FirmwareData.FirmwareUpdateState.PendingUpdate,
  currentDeviceInfo: FirmwareDeviceInfo?,
  onBack: () -> Unit,
  onContinue: () -> Unit,
): FormBodyModel {
  val mcuUpdates = pendingUpdate.mcuUpdates

  // Build data items for each MCU update
  val updateItems = mcuUpdates.map { mcuUpdate ->
    val currentVersion = currentDeviceInfo?.let { deviceInfo ->
      // For W3 multi-MCU, look up specific MCU version; for W1 use main version
      deviceInfo.mcuInfo.find { it.mcuRole == mcuUpdate.mcuRole }?.firmwareVersion
        ?: deviceInfo.version
    } ?: "Unknown"

    DataList.Data(
      title = "${mcuUpdate.mcuRole.name} (${mcuUpdate.mcuName.name})",
      sideText = mcuUpdate.fwupMode.name,
      explainer = DataList.Data.Explainer(
        title = "$currentVersion → ${mcuUpdate.version}",
        subtitle = "Size: ${formatBytes(mcuUpdate.firmware.size)}"
      )
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

  return formBodyModel(
    id = null,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Firmware Update Details",
      subline = "Review the firmware update before proceeding."
    ),
    mainContentList = immutableListOf(
      DataList(
        items = (deviceInfoItems + updateItems).toImmutableList()
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue with Update",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onContinue() }
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
