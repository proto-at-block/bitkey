package build.wallet.ui.app.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.ChartHistory
import build.wallet.pricechart.ChartType
import build.wallet.pricechart.DataPoint
import build.wallet.ui.model.render
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun PriceChartPreview(
  data: ImmutableList<DataPoint> = List(10) {
    (it + 1L) to (it + 1.0)
  }.toImmutableList(),
  history: ChartHistory = ChartHistory.YEAR,
  type: ChartType = ChartType.BTC_PRICE,
) {
  PreviewWalletTheme {
    BitcoinPriceDetailsBodyModel(
      data = data,
      history = history,
      type = type
    ).render()
  }
}
