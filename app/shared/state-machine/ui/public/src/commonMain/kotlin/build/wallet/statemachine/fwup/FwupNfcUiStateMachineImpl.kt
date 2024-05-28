package build.wallet.statemachine.fwup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.fwup.FwupNfcUiState.InNfcSessionUiState
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Hidden
import build.wallet.statemachine.fwup.FwupNfcUiState.ShowingUpdateInstructionsUiState.UpdateErrorBottomSheetState.Showing
import kotlinx.coroutines.delay

class FwupNfcUiStateMachineImpl(
  private val deviceInfoProvider: DeviceInfoProvider,
  private val fwupNfcSessionUiStateMachine: FwupNfcSessionUiStateMachine,
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
          }
        )
      }

      is InNfcSessionUiState -> {
        fwupNfcSessionUiStateMachine.model(
          props =
            FwupNfcSessionUiProps(
              isHardwareFake = props.isHardwareFake,
              firmwareData = props.firmwareData,
              transactionType = uiState.transactionType,
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
    }
  }

  @Composable
  private fun ShowingUpdateInstructionsUiModel(
    props: FwupNfcUiProps,
    state: ShowingUpdateInstructionsUiState,
    onLaunchFwup: () -> Unit,
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
    override val transactionType: FwupTransactionType = FwupTransactionType.StartFromBeginning,
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
}
