package build.wallet.statemachine.settings.full.device.resetdevice.complete

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface ResettingDeviceSuccessUiStateMachine : StateMachine<ResettingDeviceSuccessProps, ScreenModel>

data class ResettingDeviceSuccessProps(
  val onDone: () -> Unit,
)
