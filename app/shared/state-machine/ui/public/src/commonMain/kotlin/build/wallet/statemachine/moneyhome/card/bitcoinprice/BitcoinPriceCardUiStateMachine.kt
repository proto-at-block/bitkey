package build.wallet.statemachine.moneyhome.card.bitcoinprice

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * A state machine for displaying the current bitcoin price and
 * spark line as a Money Home card.
 */
interface BitcoinPriceCardUiStateMachine : StateMachine<BitcoinPriceCardUiProps, CardModel?>

data class BitcoinPriceCardUiProps(
  val onOpenPriceChart: () -> Unit,
)
