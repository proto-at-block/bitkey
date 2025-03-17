package build.wallet.statemachine.settings.full.device.wipedevice.complete

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface WipingDeviceSuccessUiStateMachine : StateMachine<WipingDeviceSuccessProps, ScreenModel>

data class WipingDeviceSuccessProps(
  val onDone: () -> Unit,
)
