package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the confirmation screen of the wipe device flow.
 */
interface WipingDeviceConfirmationUiStateMachine : StateMachine<WipingDeviceConfirmationProps, ScreenModel>

data class WipingDeviceConfirmationProps(
  val onBack: () -> Unit,
  val onWipeDevice: () -> Unit,
  val isDevicePaired: Boolean,
  val isHardwareFake: Boolean,
)
