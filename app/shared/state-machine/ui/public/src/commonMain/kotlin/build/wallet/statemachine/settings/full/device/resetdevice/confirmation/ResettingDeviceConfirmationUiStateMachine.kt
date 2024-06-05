package build.wallet.statemachine.settings.full.device.resetdevice.confirmation

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the confirmation screen of the reset device flow.
 */
interface ResettingDeviceConfirmationUiStateMachine : StateMachine<ResettingDeviceConfirmationProps, ScreenModel>

data class ResettingDeviceConfirmationProps(
  val onBack: () -> Unit,
  val onConfirmResetDevice: () -> Unit,
)
