package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.DEBUG
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.nfc.NfcException
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
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
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toast.ToastModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * State machine with debug menu that allows configuring various options for development
 * and debugging purposes. Handles showing the main list as well as screens off of the
 * list. The list UI is managed by [DebugMenuListStateMachine].
 */
data object DebugMenuScreen : Screen

@BitkeyInject(ActivityScope::class, boundTypes = [DebugMenuScreenPresenter::class])
class DebugMenuScreenPresenter(
  private val analyticsUiStateMachine: AnalyticsUiStateMachine,
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
  private val onboardingCompletionService: OnboardingCompletionService,
  private val inheritanceUpsellService: InheritanceUpsellService,
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

    return when (val state = uiState) {
      is DebugMenuState.ShowingDebugMenu ->
        debugMenuListStateMachine.model(
          props = DebugMenuListProps(
            navigator = navigator,
            firmwareData = firmwareData,
            onSetState = { uiState = it },
            onClose = { navigator.exit() },
            onPasteboardCopy = { pasteboardToast = it }
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
            screenPresentationStyle = Modal,
            eventTrackerContext = DEBUG
          )
        )

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
          message = when (state) {
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
