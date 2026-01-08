package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the hardware confirmation screen shown before NFC signing.
 * This screen prompts the user to review transaction details on their Bitkey device.
 */
interface HardwareConfirmationUiStateMachine : StateMachine<HardwareConfirmationUiProps, ScreenModel>

/**
 * @property onBack - Handler for back/cancel action
 * @property onConfirm - Handler for when user confirms they want to proceed with signing
 */
data class HardwareConfirmationUiProps(
  val onBack: () -> Unit,
  val onConfirm: () -> Unit,
)
