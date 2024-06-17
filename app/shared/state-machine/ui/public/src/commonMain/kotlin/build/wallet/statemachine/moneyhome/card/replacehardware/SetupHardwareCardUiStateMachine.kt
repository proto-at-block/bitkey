package build.wallet.statemachine.moneyhome.card.replacehardware

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * State machine which renders a [CardModel] prompting the user to setup
 * a Bitkey device in [MoneyHomeStateMachine].
 * The model is null when there is no card to show.
 */
interface SetupHardwareCardUiStateMachine : StateMachine<SetupHardwareCardUiProps, CardModel?>

/**
 * @property onReplaceDevice invoked when card is tapped
 */
data class SetupHardwareCardUiProps(
  val onReplaceDevice: () -> Unit,
  val deviceInfo: FirmwareDeviceInfo?,
)
