package build.wallet.pricechart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.compose.collections.immutableListOf
import build.wallet.pricechart.ui.BitcoinPriceChartScreen
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class BitcoinPriceDetailsBodyModel(
  val data: ImmutableList<DataPoint> = immutableListOf(),
  val history: ChartHistory = ChartHistory.YEAR,
  val type: ChartType = ChartType.BTC_PRICE,
  val isLoading: Boolean = true,
  val selectedPoint: DataPoint? = null,
  val selectedPointPrimaryText: String? = null,
  val selectedPointSecondaryText: String? = null,
  val selectedPointPeriodText: String? = null,
  val selectedPointChartText: String? = null,
  val selectedPriceDirection: PriceDirection = PriceDirection.STABLE,
  val failedToLoad: Boolean = false,
  val formatFiatValue: (value: Double) -> String = { it.toString() },
  val onPointSelected: (DataPoint?) -> Unit = {},
  val onChartTypeSelected: (ChartType) -> Unit = {},
  val onChartHistorySelected: (ChartHistory) -> Unit = {},
  override val onBack: () -> Unit = { },
  val toolbarModel: ToolbarModel = ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
  ),
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    BitcoinPriceChartScreen(modifier, model = this)
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
