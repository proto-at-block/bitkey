package build.wallet.statemachine.settings.full.device.wipedevice.processing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(ActivityScope::class)
class WipingDeviceProgressUiStateMachineImpl : WipingDeviceProgressUiStateMachine {
  @Composable
  override fun model(props: WipingDeviceProgressProps): ScreenModel {
    LaunchedEffect("processing-delay") {
      delay(3.seconds)
      props.onCompleted()
    }
    return ScreenModel(
      body = LoadingBodyModel(
        id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_IN_PROGRESS,
        message = "Your Bitkey device is being wiped"
      )
    )
  }
}
