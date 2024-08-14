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
    Res.string.chart_history_label_day,
    1.days,
    75,
    Res.string.chart_history_label_day_diff
  ),
  WEEK(
    Res.string.chart_history_label_week,
    7.days,
    75,
    Res.string.chart_history_label_week_diff
  ),
  MONTH(
    Res.string.chart_history_label_month,
    30.days,
    100,
    Res.string.chart_history_label_month_diff
  ),
  YEAR(
    Res.string.chart_history_label_year,
    365.days,
    150,
    Res.string.chart_history_label_year_diff
  ),
  ALL(
    Res.string.chart_history_label_all,
    3650.days,
    150,
    Res.string.chart_history_label_all_diff
  ),
}
