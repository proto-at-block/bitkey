package build.wallet.statemachine.moneyhome.card.bitcoinprice

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * A state machine for displaying the current bitcoin price and
 * spark line as a Money Home card.
 */
interface BitcoinPriceCardUiStateMachine : StateMachine<BitcoinPriceCardUiProps, CardModel?>

data class BitcoinPriceCardUiProps(
  val fullAccountId: FullAccountId,
  val f8eEnvironment: F8eEnvironment,
  val onOpenPriceChart: () -> Unit,
)
