package build.wallet.statemachine.settings.full.device.resetdevice.processing

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface ResettingDeviceProgressUiStateMachine :
  StateMachine<ResettingDeviceProgressProps, ScreenModel>

data class ResettingDeviceProgressProps(
  val onCompleted: () -> Unit,
)
