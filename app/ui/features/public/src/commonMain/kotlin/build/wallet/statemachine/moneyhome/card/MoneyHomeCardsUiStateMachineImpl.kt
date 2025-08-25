package build.wallet.statemachine.moneyhome.card

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.buildImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiStateMachine
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class MoneyHomeCardsUiStateMachineImpl(
  private val gettingStartedCardUiStateMachine: GettingStartedCardUiStateMachine,
  private val startSweepCardUiStateMachine: StartSweepCardUiStateMachine,
  private val bitcoinPriceCardUiStateMachine: BitcoinPriceCardUiStateMachine,
  private val inheritanceCardUiStateMachine: InheritanceCardUiStateMachine,
) : MoneyHomeCardsUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeCardsProps): CardListModel {
    return CardListModel(
      cards = buildImmutableList {
        inheritanceCardUiStateMachine.model(props.inheritanceCardUiProps).forEach {
          add(it)
        }
        add(startSweepCardUiStateMachine.model(props.startSweepCardUiProps))
        add(bitcoinPriceCardUiStateMachine.model(props.bitcoinPriceCardUiProps))
        add(gettingStartedCardUiStateMachine.model(props.gettingStartedCardUiProps))
      }
        .filterNotNull()
        .toImmutableList()
    )
  }
}
