package build.wallet.statemachine.moneyhome.card.bitcoinprice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.compose.collections.immutableListOf
import build.wallet.pricechart.BitcoinPriceCardPreference
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.list.ListItemModel

class BitcoinPriceCardUiStateMachineImpl(
  private val bitcoinPriceCardPreference: BitcoinPriceCardPreference,
) : BitcoinPriceCardUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceCardUiProps): CardModel? {
    val enabled by remember { bitcoinPriceCardPreference.isEnabled }.collectAsState()
    if (!enabled) {
      return null
    }

    return createPriceChartCard(props.onOpenPriceChart)
  }
}

private fun createPriceChartCard(onClick: () -> Unit): CardModel {
  return CardModel(
    title = LabelModel.StringWithStyledSubstringModel.from(
      "Bitcoin Price",
      emptyList()
    ),
    content = CardModel.CardContent.DrillList(
      items = immutableListOf(
        ListItemModel(
          title = "Click me!",
          onClick = onClick
        )
      )
    ),
    style = CardModel.CardStyle.Outline
  )
}
