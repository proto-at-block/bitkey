package build.wallet.ui.app.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.pricechart.BitcoinPriceDetailsBodyModel
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ChartType
import build.wallet.pricechart.DataPoint
import build.wallet.ui.model.render
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.lightStyleDictionaryColorsDesignSystemUpdates
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun PriceChartPreview() {
  PreviewWalletTheme {
    BitcoinPriceDetailsBodyModel(
      data = List(10) {
        DataPoint((it + 1L), (it + 1.0))
      }.toImmutableList(),
      range = ChartRange.YEAR,
      type = ChartType.BTC_PRICE
    ).render()
  }
}

@Preview
@Composable
fun YourBalanceEmptyStateDesignSystemV2LightPreview() {
  PreviewWalletTheme(
    theme = Theme.LIGHT,
    backgroundColor = lightStyleDictionaryColorsDesignSystemUpdates.background,
    designSystemUpdatesEnabled = true
  ) {
    BitcoinPriceDetailsBodyModel(
      type = ChartType.BALANCE,
      isLoading = false,
      fiatCurrencyCode = "USD"
    ).render()
  }
}
