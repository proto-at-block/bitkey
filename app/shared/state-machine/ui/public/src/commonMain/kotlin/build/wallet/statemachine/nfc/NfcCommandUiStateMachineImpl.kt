package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.NfcErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.nfc.NfcCommandData.ExecutingNfcCommandData
import build.wallet.statemachine.data.nfc.NfcCommandData.NfcCommandErrorData
import build.wallet.statemachine.data.nfc.NfcCommandData.NfcCommandExecutedData
import build.wallet.statemachine.data.nfc.NfcCommandData.NfcDisabledData
import build.wallet.statemachine.data.nfc.NfcCommandData.NfcNotAvailableData
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Connected
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Searching
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Success
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import kotlinx.coroutines.delay

class NfcCommandUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val deviceInfoProvider: DeviceInfoProvider,
) : NfcCommandUiStateMachine {
  @Composable
  override fun model(props: NfcUiProps): ScreenModel {
    return when (props.nfcCommandData) {
      is NfcNotAvailableData ->
        NoNfcMessageModel(onBack = props.onCancel)
          .asScreen(props.screenPresentationStyle)

      is NfcDisabledData -> {
        var navigatingToEnableNfc by remember { mutableStateOf(false) }

        // Navigate to system settings to enable NFC availability.
        if (navigatingToEnableNfc) {
          enableNfcNavigator.navigateToEnableNfc(
            // Check NFC availability after customer comes back to the app.
            onReturn = props.nfcCommandData.checkNfcAvailability
          )
        }

        EnableNfcInstructionsModel(
          onBack = props.onCancel,
          onEnableClick = { navigatingToEnableNfc = true }
        ).asScreen(props.screenPresentationStyle)
      }

      is ExecutingNfcCommandData ->
        NfcBodyModel(
          text = "Hold device here behind phone",
          status =
            when (props.nfcCommandData.isTagConnected) {
              true -> Connected(props.onCancel)
              false -> Searching(props.onCancel)
            },
          eventTrackerScreenInfo =
            EventTrackerScreenInfo(
              eventTrackerScreenId = NfcEventTrackerScreenId.NFC_INITIATE,
              eventTrackerScreenIdContext = props.nfcCommandData.eventTrackerContext
            )
        ).asFullScreen()

      is NfcCommandExecutedData -> {
        LaunchedEffect("proceed-after-success") {
          // Delay moving data state machine forward for some time
          // while we are showing success screen to customer.
          delay(
            NfcSuccessScreenDuration(
              devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
              isHardwareFake = props.isHardwareFake
            )
          )
          props.nfcCommandData.proceed()
        }
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo =
            EventTrackerScreenInfo(
              eventTrackerScreenId = NfcEventTrackerScreenId.NFC_SUCCESS,
              eventTrackerScreenIdContext = props.nfcCommandData.eventTrackerContext
            )
        ).asFullScreen()
      }

      is NfcCommandErrorData ->
        NfcErrorFormBodyModel(
          error = props.nfcCommandData.error,
          onPrimaryButtonClick = props.onCancel,
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_FAILURE,
          eventTrackerScreenIdContext = props.nfcCommandData.eventTrackerContext
        ).asScreen(props.screenPresentationStyle)
    }
  }
}
