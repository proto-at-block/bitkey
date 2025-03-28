package build.wallet.ui.app.pricechart

import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.pricechart.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PriceChartScreenSnapshotTest : FunSpec({
  val paparazzi = paparazziExtension()
  val chartData = generateChartData(150)

  test("Bitcoin Price chart - loading") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = emptyImmutableList(),
              range = ChartRange.YEAR,
              type = ChartType.BTC_PRICE
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - your balance") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = chartData.map {
                BalanceAt(Instant.fromEpochMilliseconds(it.x), it.y, it.y)
              }.toImmutableList(),
              type = ChartType.BALANCE,
              isLoading = false,
              fiatCurrencyCode = "USD",
              selectedPointData = SelectedPointData.Balance(
                isUserSelected = false,
                primaryFiatText = "$80.00",
                secondaryFiatText = "+10.00% Past day",
                primaryBtcText = "500 sats",
                secondaryBtcText = "+30.00% Past day"
              )
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - empty") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = emptyImmutableList(),
              type = ChartType.BALANCE,
              isLoading = false,
              fiatCurrencyCode = "USD",
              selectedPointData = SelectedPointData.Balance(
                isUserSelected = false,
                primaryFiatText = "$80.00",
                secondaryFiatText = "+10.00% Past day",
                primaryBtcText = "500 sats",
                secondaryBtcText = "+30.00% Past day"
              )
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - your balance with selection") {
    paparazzi.snapshot {
      val data = chartData.map {
        BalanceAt(Instant.fromEpochMilliseconds(it.x), it.y, it.y)
      }.toImmutableList()
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = data,
              type = ChartType.BALANCE,
              isLoading = false,
              fiatCurrencyCode = "USD",
              selectedPoint = data[data.size / 2],
              selectedPointData = SelectedPointData.Balance(
                isUserSelected = false,
                primaryFiatText = "$80.00",
                secondaryFiatText = "+10.00% Past day",
                primaryBtcText = "500 sats",
                secondaryBtcText = "+30.00% Past day"
              )
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - no selection") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = chartData,
              selectedPointData = SelectedPointData.BtcPrice(
                isUserSelected = false,
                primaryText = "$80.00",
                secondaryText = "10.00%",
                secondaryTimePeriodText = "Past year",
                direction = PriceDirection.UP
              ),
              isLoading = false
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - with selection at start") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = chartData,
              selectedPointData = SelectedPointData.BtcPrice(
                isUserSelected = false,
                primaryText = "$80.00",
                secondaryText = "10.00%",
                secondaryTimePeriodText = "Past year",
                direction = PriceDirection.UP
              ),
              selectedPointTimestamp = "Yesterday 12:31am",
              selectedPoint = chartData.first(),
              isLoading = false
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - with selection at end") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = chartData,
              selectedPointData = SelectedPointData.BtcPrice(
                isUserSelected = false,
                primaryText = "$80.00",
                secondaryText = "10.00%",
                secondaryTimePeriodText = "Past year",
                direction = PriceDirection.UP
              ),
              selectedPointTimestamp = "Yesterday 12:31am",
              selectedPoint = chartData.last(),
              isLoading = false
            )
        )
      )
    }
  }

  test("Bitcoin Price chart - with selection") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body =
            BitcoinPriceDetailsBodyModel(
              data = chartData,
              selectedPointData = SelectedPointData.BtcPrice(
                isUserSelected = false,
                primaryText = "$80.00",
                secondaryText = "10.00%",
                secondaryTimePeriodText = "Past year",
                direction = PriceDirection.UP
              ),
              selectedPointTimestamp = "Yesterday 12:31am",
              selectedPoint = chartData[chartData.lastIndex / 3],
              isLoading = false
            )
        )
      )
    }
  }
})

private fun generateChartData(pointCount: Int): ImmutableList<DataPoint> {
  return buildImmutableList {
    for (i in 0 until pointCount) {
      val y = abs(sin(i * PI / 30) * 20 + cos(i * PI / 15) * 10)
      add(DataPoint(Clock.System.now().plus(i * (7 * 24), DateTimeUnit.HOUR).epochSeconds, y))
    }
  }
}
