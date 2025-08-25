package build.wallet.statemachine.moneyhome.card

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiProps
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiProps

/**
 * State Machine which composes card state machines to be rendered in [MoneyHomeStateMachine]
 */
interface MoneyHomeCardsUiStateMachine : StateMachine<MoneyHomeCardsProps, CardListModel>

data class MoneyHomeCardsProps(
  val gettingStartedCardUiProps: GettingStartedCardUiProps,
  val startSweepCardUiProps: StartSweepCardUiProps,
  val bitcoinPriceCardUiProps: BitcoinPriceCardUiProps,
  val inheritanceCardUiProps: InheritanceCardUiProps,
)
