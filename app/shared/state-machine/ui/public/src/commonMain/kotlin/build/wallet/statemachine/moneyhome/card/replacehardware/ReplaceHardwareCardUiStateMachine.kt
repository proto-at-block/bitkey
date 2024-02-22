package build.wallet.statemachine.moneyhome.card.replacehardware

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * State machine which renders a [CardModel] representing "Replace your Bitkey" in [MoneyHomeStateMachine].
 * The model is null when there is no card to show
 */
interface ReplaceHardwareCardUiStateMachine : StateMachine<ReplaceHardwareCardUiProps, CardModel?>

/**
 * @property onReplaceDevice invoked when card is tapped
 */
data class ReplaceHardwareCardUiProps(
  val onReplaceDevice: () -> Unit,
)
