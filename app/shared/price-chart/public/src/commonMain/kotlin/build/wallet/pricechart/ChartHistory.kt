package build.wallet.pricechart

import bitkey.shared.ui_core_public.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class ChartHistory(
  val label: StringResource,
  val days: Duration,
  val maxPricePoints: Int,
  val diffLabel: StringResource,
) {
  DAY(
    label = Res.string.chart_history_label_day,
    days = 1.days,
    maxPricePoints = 75,
    diffLabel = Res.string.chart_history_label_day_diff
  ),
  WEEK(
    label = Res.string.chart_history_label_week,
    days = 7.days,
    maxPricePoints = 75,
    diffLabel = Res.string.chart_history_label_week_diff
  ),
  MONTH(
    label = Res.string.chart_history_label_month,
    days = 30.days,
    maxPricePoints = 100,
    diffLabel = Res.string.chart_history_label_month_diff
  ),
  YEAR(
    label = Res.string.chart_history_label_year,
    days = 365.days,
    maxPricePoints = 150,
    diffLabel = Res.string.chart_history_label_year_diff
  ),
  ALL(
    label = Res.string.chart_history_label_all,
    days = 3650.days,
    maxPricePoints = 170,
    diffLabel = Res.string.chart_history_label_all_diff
  ),
}
