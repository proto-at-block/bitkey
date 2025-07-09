package build.wallet.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.compose.collections.immutableListOf
import build.wallet.pricechart.ui.ChartScreen
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class BitcoinPriceDetailsBodyModel(
  val data: ImmutableList<DataPoint> = immutableListOf(),
  val range: ChartRange = ChartRange.YEAR,
  val type: ChartType = ChartType.BTC_PRICE,
  val isLoading: Boolean = true,
  val failedToLoad: Boolean = false,
  val fiatCurrencyCode: String? = null,
  val selectedPoint: DataPoint? = null,
  val selectedPointData: SelectedPointData? = null,
  val selectedPointTimestamp: String? = null,
  val formatFiatValue: (value: Double, precise: Boolean) -> String = { v, _ -> v.toString() },
  val onPointSelected: (DataPoint?) -> Unit = {},
  val onChartTypeSelected: (ChartType) -> Unit = {},
  val onChartRangeSelected: (ChartRange) -> Unit = {},
  val onBuy: () -> Unit = {},
  val onTransfer: () -> Unit = {},
  override val onBack: () -> Unit = { },
  val toolbarModel: ToolbarModel = ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
  ),
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    ChartScreen(modifier, model = this)
  }
}

enum class PriceDirection(val orientation: Float) {
  DOWN(180f),
  UP(0f),
  STABLE(90f),
  ;

  companion object {
    fun from(price: BigDecimal): PriceDirection {
      return when {
        price == BigDecimal.ZERO -> STABLE
        price.isPositive -> UP
        else -> DOWN
      }
    }
  }
}
