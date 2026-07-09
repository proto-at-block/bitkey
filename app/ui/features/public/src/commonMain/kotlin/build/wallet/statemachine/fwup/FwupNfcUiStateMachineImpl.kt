package build.wallet.statemachine.fwup

import androidx.compose.runtime.*
import bitkey.account.AccountConfig
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.ModalFullScreen
import build.wallet.statemachine.fwup.FwupNfcUiState.*
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Hidden
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Showing
import build.wallet.statemachine.fwup.FwupTransactionType.StartFromBeginning
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.FirmwareUpdate as FirmwareUpdateHelpContent
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class FwupNfcUiStateMachineImpl(
  private val deviceInfoProvider: DeviceInfoProvider,
  private val fwupNfcSessionUiStateMachine: FwupNfcSessionUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val accountConfigService: AccountConfigService,
  private val keyboxDao: KeyboxDao,
  private val eventTracker: EventTracker,
) : FwupNfcUiStateMachine {
  @Composable
  override fun model(props: FwupNfcUiProps): ScreenModel {
    var uiState: FwupNfcUiState by remember {
      mutableStateOf(ShowingUpdateInstructionsUiState())
    }

    return when (val state = uiState) {
      is ShowingUpdateInstructionsUiState -> {
        ShowingUpdateInstructionsUiModel(
          props = props,
          state = state,
          onLaunchFwup = {
            uiState = InNfcSessionUiState(state.transactionType)
          },
          onHelpClick = { hardwareType ->
            uiState = ShowingHelpUiState(
              transactionType = state.transactionType,
              hardwareType = hardwareType
            )
          },
          onReleaseNotes = {
            uiState = ReleaseNotesUiState()
          }
        )
      }

      is InNfcSessionUiState -> {
        fwupNfcSessionUiStateMachine.model(
          props =
            FwupNfcSessionUiProps(
              transactionType = uiState.transactionType,
              selectedMcuUpdates = props.selectedMcuUpdates,
              hardwareTypeOverride = props.hardwareTypeOverride,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              onBack = {
                uiState = ShowingUpdateInstructionsUiState()
              },
              onDone = props.onDone,
              onError = { error, updateWasInProgress, transactionType ->
                uiState =
                  ShowingUpdateInstructionsUiState(
                    updateErrorBottomSheetState = Showing(error, updateWasInProgress),
                    transactionType = transactionType
                  )
              }
            )
        )
      }

      is ReleaseNotesUiState -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/en-US/releases",
              onClose = {
                uiState = ShowingUpdateInstructionsUiState()
              }
            )
          }
        ).asModalScreen()
      }

      is ShowingHelpUiState -> {
        val devicePlatform = remember { deviceInfoProvider.getDeviceInfo().devicePlatform }
        ScreenModel(
          body = HardwareConfirmationHelpBodyModel(
            onBack = {
              uiState = ShowingUpdateInstructionsUiState(transactionType = state.transactionType)
            },
            content = FirmwareUpdateHelpContent,
            devicePlatform = devicePlatform
          ),
          presentationStyle = ModalFullScreen,
          themePreference = fwupHelpThemePreference(
            devicePlatform = devicePlatform,
            hardwareType = state.hardwareType
          )
        )
      }
    }
  }

  @Composable
  private fun ShowingUpdateInstructionsUiModel(
    props: FwupNfcUiProps,
    state: ShowingUpdateInstructionsUiState,
    onLaunchFwup: () -> Unit,
    onHelpClick: (HardwareType) -> Unit,
    onReleaseNotes: () -> Unit,
  ): ScreenModel {
    val activeKeybox by remember {
      keyboxDao.activeKeybox().map { it.get() }
    }.collectAsState(initial = null)
    val defaultConfig by remember {
      accountConfigService.activeOrDefaultConfig()
    }.collectAsState()
    val hardwareType = props.hardwareTypeOverride
      ?: activeKeybox?.config?.hardwareType
      ?: extractHardwareType(defaultConfig)
    var isRelaunchingFwup: Boolean by remember { mutableStateOf(false) }
    var updateErrorBottomSheetState: UpdateErrorBottomSheetState
      by remember { mutableStateOf(state.updateErrorBottomSheetState) }

    if (isRelaunchingFwup && updateErrorBottomSheetState == Hidden) {
      LaunchedEffect("launch-fwup") {
        // Wait to show the error sheet dismissed before re-launching FWUP
        delay(5)
        onLaunchFwup()
      }
    }

    return FwupUpdateDeviceModel(
      devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
      hardwareType = hardwareType,
      onClose = props.onDone,
      onHelpClick = { onHelpClick(hardwareType) },
      onLaunchFwup = {
        eventTracker.track(Action.ACTION_APP_TAP_FWUP_CARD)
        onLaunchFwup()
      },
      onReleaseNotes = onReleaseNotes,
      bottomSheetModel =
        when (val sheetState = updateErrorBottomSheetState) {
          is Hidden -> null
          is Showing ->
            when (sheetState.error) {
              is NfcException.CommandErrorUnauthenticated ->
                FwupUpdateDeviceBottomSheet.UnauthenticatedErrorModel(
                  onClosed = { updateErrorBottomSheetState = Hidden }
                )
              is NfcException.PreviousMcuUpdateNotApplied ->
                FwupUpdateDeviceBottomSheet.PreviousMcuUpdateNotAppliedModel(
                  onClosed = { updateErrorBottomSheetState = Hidden },
                  onRelaunchFwup = {
                    updateErrorBottomSheetState = Hidden
                    isRelaunchingFwup = true
                  }
                )
              else ->
                FwupUpdateDeviceBottomSheet.UpdateErrorModel(
                  error = sheetState.error,
                  deviceInfo = deviceInfoProvider.getDeviceInfo(),
                  wasInProgress = sheetState.updateWasInProgress,
                  onClosed = { updateErrorBottomSheetState = Hidden },
                  onRelaunchFwup = {
                    updateErrorBottomSheetState = Hidden
                    isRelaunchingFwup = true
                  }
                )
            }
        }
    )
  }

  private fun extractHardwareType(accountConfig: AccountConfig): HardwareType {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.hardwareType
      is DefaultAccountConfig -> accountConfig.hardwareType ?: HardwareType.W1
      else -> HardwareType.W1
    }
  }

  private fun fwupHelpThemePreference(
    devicePlatform: DevicePlatform,
    hardwareType: HardwareType,
  ): ThemePreference =
    when {
      devicePlatform == DevicePlatform.Android -> fwupThemePreference(devicePlatform)
      hardwareType == HardwareType.W3 -> fwupThemePreference(devicePlatform)
      else -> ThemePreference.Manual(Theme.DARK)
    }
}

private sealed interface FwupNfcUiState {
  val transactionType: FwupTransactionType

  data class ShowingUpdateInstructionsUiState(
    val updateErrorBottomSheetState: UpdateErrorBottomSheetState = Hidden,
    override val transactionType: FwupTransactionType = StartFromBeginning(),
  ) : FwupNfcUiState {
    sealed interface UpdateErrorBottomSheetState {
      data object Hidden : UpdateErrorBottomSheetState

      /**
       * @property updateWasInProgress: Whether FWUP was in progress before showing this state.
       * Used to show more specific error messaging to the customer.
       */
      data class Showing(
        val error: NfcException,
        val updateWasInProgress: Boolean,
      ) : UpdateErrorBottomSheetState
    }
  }

  data class InNfcSessionUiState(override val transactionType: FwupTransactionType) : FwupNfcUiState

  data class ReleaseNotesUiState(
    override val transactionType: FwupTransactionType = StartFromBeginning(),
  ) : FwupNfcUiState

  data class ShowingHelpUiState(
    val hardwareType: HardwareType,
    override val transactionType: FwupTransactionType = StartFromBeginning(),
  ) : FwupNfcUiState
}
