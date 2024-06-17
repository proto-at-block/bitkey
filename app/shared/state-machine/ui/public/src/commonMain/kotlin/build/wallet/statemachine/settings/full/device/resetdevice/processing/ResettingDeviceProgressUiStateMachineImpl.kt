package build.wallet.statemachine.settings.full.device.resetdevice.processing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class ResettingDeviceProgressUiStateMachineImpl : ResettingDeviceProgressUiStateMachine {
  @Composable
  override fun model(props: ResettingDeviceProgressProps): ScreenModel {
    LaunchedEffect("processing-delay") {
      delay(3.seconds)
      props.onCompleted()
    }
    return ScreenModel(
      body = LoadingBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_IN_PROGRESS,
        message = "Your Bitkey device is resetting"
      )
    )
  }
}
