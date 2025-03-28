package build.wallet.pricechart

import bitkey.ui.framework_public.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class ChartRange(
  /** Label text identifying this range in a list of options. */
  val label: StringResource,
  /** Expanded label text indicating how long this range represents. */
  val diffLabel: StringResource,
  /** [Duration] representation of the range. */
  val duration: Duration,
  /** [Duration] between data points for the range. */
  val interval: Duration,
  /** Maximum number of data points for this range.  */
  val maxPricePoints: Int,
) {
  DAY(
    label = Res.string.chart_history_label_day,
    diffLabel = Res.string.chart_history_label_day_diff,
    duration = 1.days,
    interval = 10.minutes,
    maxPricePoints = 75
  ),
  WEEK(
    label = Res.string.chart_history_label_week,
    diffLabel = Res.string.chart_history_label_week_diff,
    duration = 7.days,
    interval = 1.hours,
    maxPricePoints = 75
  ),
  MONTH(
    label = Res.string.chart_history_label_month,
    diffLabel = Res.string.chart_history_label_month_diff,
    duration = 30.days,
    interval = 1.days,
    maxPricePoints = 100
  ),
  YEAR(
    label = Res.string.chart_history_label_year,
    diffLabel = Res.string.chart_history_label_year_diff,
    duration = 365.days,
    interval = 1.days,
    maxPricePoints = 150
  ),
  ALL(
    label = Res.string.chart_history_label_all,
    diffLabel = Res.string.chart_history_label_all_diff,
    duration = 3650.days,
    interval = 1.days,
    maxPricePoints = 170
  ),
}
