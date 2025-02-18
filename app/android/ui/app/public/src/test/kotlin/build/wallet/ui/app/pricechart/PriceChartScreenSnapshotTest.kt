package build.wallet.ui.app.pricechart

import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.pricechart.*
import build.wallet.ui.model.render
import build.wallet.ui.tooling.PreviewWalletTheme
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PriceChartScreenSnapshotTest : FunSpec({
  val paparazzi = paparazziExtension()
  val chartData = generateChartData(150)

  test("Bitcoin Price chart - loading") {
    paparazzi.snapshot {
      PriceChartPreview(
        data = emptyImmutableList(),
        history = ChartHistory.YEAR,
        type = ChartType.BTC_PRICE
      )
    }
  }

  test("Bitcoin Price chart - your balance") {
    paparazzi.snapshot {
      PriceChartPreview(
        data = emptyImmutableList(),
        history = ChartHistory.YEAR,
        type = ChartType.BALANCE
      )
    }
  }

  test("Bitcoin Price chart - no selection") {
    paparazzi.snapshot {
      PreviewWalletTheme {
        BitcoinPriceDetailsBodyModel(
          data = chartData,
          selectedPointPrimaryText = "$80.00",
          selectedPointSecondaryText = "10.00%",
          selectedPointPeriodText = "Past year",
          selectedPriceDirection = PriceDirection.UP,
          isLoading = false
        ).render()
      }
    }
  }

  test("Bitcoin Price chart - with selection at start") {
    paparazzi.snapshot {
      BitcoinPriceDetailsBodyModel(
        data = chartData,
        selectedPointPrimaryText = "$80.00",
        selectedPointSecondaryText = "10.00%",
        selectedPriceDirection = PriceDirection.UP,
        selectedPointChartText = "Yesterday 12:31am",
        selectedPoint = chartData.first(),
        isLoading = false
      ).render()
    }
  }

  test("Bitcoin Price chart - with selection at end") {
    paparazzi.snapshot {
      BitcoinPriceDetailsBodyModel(
        data = chartData,
        selectedPointPrimaryText = "$80.00",
        selectedPointSecondaryText = "10.00%",
        selectedPriceDirection = PriceDirection.UP,
        selectedPointChartText = "Yesterday 12:31am",
        selectedPoint = chartData.last(),
        isLoading = false
      ).render()
    }
  }

  test("Bitcoin Price chart - with selection") {
    paparazzi.snapshot {
      PreviewWalletTheme {
        BitcoinPriceDetailsBodyModel(
          data = chartData,
          selectedPointPrimaryText = "$80.00",
          selectedPointSecondaryText = "10.00%",
          selectedPriceDirection = PriceDirection.UP,
          selectedPointChartText = "Yesterday 12:31am",
          selectedPoint = chartData[chartData.lastIndex / 3],
          isLoading = false
        ).render()
      }
    }
  }
})

private fun generateChartData(pointCount: Int): ImmutableList<DataPoint> {
  return buildImmutableList {
    for (i in 0 until pointCount) {
      val y = abs(sin(i * PI / 30) * 20 + cos(i * PI / 15) * 10)
      add(DataPoint(i.toLong(), y))
    }
  }
}
