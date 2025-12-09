package build.wallet.statemachine.fwup

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.fwup.semverToInt
import build.wallet.keybox.KeyboxDao
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.statemachine.fwup.FwupNfcUiState.*
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Hidden
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Showing
import build.wallet.statemachine.fwup.FwupTransactionType.StartFromBeginning
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.decodeHex

@BitkeyInject(ActivityScope::class)
class FwupNfcUiStateMachineImpl(
  private val deviceInfoProvider: DeviceInfoProvider,
  private val fwupNfcSessionUiStateMachine: FwupNfcSessionUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val keyboxDao: KeyboxDao,
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
              onBack = {
                uiState = ShowingUpdateInstructionsUiState()
              },
              onDone = { expectedVersion ->
                uiState = VerifyingUpdateUiState(expectedVersion)
              },
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

      is VerifyingUpdateUiState -> {
        nfcSessionUIStateMachine.model(
          props = NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val deviceInfo = commands.getDeviceInfo(session)
              if (deviceInfo.version != state.expectedVersion) {
                throw NfcException.CommandError("Version mismatch: expected ${state.expectedVersion}, got ${deviceInfo.version}")
              }

              val minFirmwareVersion = fingerprintResetMinFirmwareVersionFeatureFlag.flagValue().value.value
              val targetVersionInt = semverToInt(deviceInfo.version)
              val minVersionInt = semverToInt(minFirmwareVersion)
              val keybox = keyboxDao.activeKeybox().first().getOrThrow()

              // check if we need to provision app auth key after fwup
              if (targetVersionInt >= minVersionInt && keybox != null) {
                commands.provisionAppAuthKey(session, keybox.activeAppKeyBundle.authKey.value.decodeHex())
              }
            },
            onSuccess = {
              uiState = VerificationSuccessUiState
            },
            onCancel = {
              uiState = VerificationErrorUiState
            },
            onError = { error ->
              uiState = VerificationErrorUiState
              true // We handled the error
            },
            needsAuthentication = false,
            hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.Required(),
            screenPresentationStyle = ScreenPresentationStyle.ModalFullScreen,
            eventTrackerContext = NfcEventTrackerScreenIdContext.FWUP
          )
        )
      }

      is VerificationSuccessUiState -> {
        SuccessBodyModel(
          title = "Firmware updated",
          message = "Your Bitkey is now running the latest firmware and ready to use.",
          primaryButtonModel = ButtonDataModel(
            text = "Done",
            onClick = props.onDone
          ),
          id = FwupEventTrackerScreenId.FWUP_VERIFICATION_SUCCESS
        ).asModalScreen()
      }

      is VerificationErrorUiState -> {
        ErrorFormBodyModel(
          title = "Firmware update failed",
          subline = "Your Bitkey was unable to install the firmware update. Please try again.",
          primaryButton = ButtonDataModel(
            text = "Try again",
            onClick = {
              uiState = ShowingUpdateInstructionsUiState()
            }
          ),
          eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_VERIFICATION_FAILURE
        ).asModalScreen()
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
    }
  }

  @Composable
  private fun ShowingUpdateInstructionsUiModel(
    props: FwupNfcUiProps,
    state: ShowingUpdateInstructionsUiState,
    onLaunchFwup: () -> Unit,
    onReleaseNotes: () -> Unit,
  ): ScreenModel {
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
      onClose = props.onDone,
      onLaunchFwup = onLaunchFwup,
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
              else ->
                FwupUpdateDeviceBottomSheet.UpdateErrorModel(
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
}

private sealed interface FwupNfcUiState {
  val transactionType: FwupTransactionType

  data class ShowingUpdateInstructionsUiState(
    val updateErrorBottomSheetState: UpdateErrorBottomSheetState = Hidden,
    override val transactionType: FwupTransactionType = StartFromBeginning,
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

  data class VerifyingUpdateUiState(
    val expectedVersion: String,
    override val transactionType: FwupTransactionType = StartFromBeginning,
  ) : FwupNfcUiState

  data object VerificationSuccessUiState : FwupNfcUiState {
    override val transactionType: FwupTransactionType = StartFromBeginning
  }

  data object VerificationErrorUiState : FwupNfcUiState {
    override val transactionType: FwupTransactionType = StartFromBeginning
  }

  data class ReleaseNotesUiState(
    override val transactionType: FwupTransactionType = StartFromBeginning,
  ) : FwupNfcUiState
}
