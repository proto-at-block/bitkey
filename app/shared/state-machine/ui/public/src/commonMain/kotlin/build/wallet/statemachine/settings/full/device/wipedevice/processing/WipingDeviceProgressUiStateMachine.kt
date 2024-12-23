package build.wallet.statemachine.settings.full.device.wipedevice.processing

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface WipingDeviceProgressUiStateMachine :
  StateMachine<WipingDeviceProgressProps, ScreenModel>

data class WipingDeviceProgressProps(
  val onCompleted: () -> Unit,
)
