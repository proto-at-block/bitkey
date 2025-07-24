package build.wallet.pricechart.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.DataPoint
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.*

/**
 * Contains a list of [DataPoint]s provides various facilities to
 * translate data to and from the rendered Canvas chart.
 */
internal data class ChartDataState(
  val data: ImmutableList<DataPoint>,
  val intervals: Int,
  val pathSize: Float,
  val chartRange: ChartRange,
) {
  private val yMin: Double
  private val yMax: Double
  private val intervalValue: Double
  private var yFloor: Double
  private var yCeil: Double
  private val range: Double
  val precise: Boolean

  init {
    val dataYMin = data.minOfOrNull { it.y } ?: 0.0
    val dataYMax = data.maxOfOrNull { it.y } ?: 1.0

    if (dataYMax == dataYMin) {
      // If data contains only one or the same repeated y value
      // create a minimal range around it and enable precise labels.
      yMin = (dataYMin - 1).coerceAtLeast(0.0)
      yMax = dataYMax + 1
      precise = true
    } else {
      yMin = dataYMin
      yMax = dataYMax
      precise = false
    }

    var rawRange = yMax - yMin
    val rawInterval = (rawRange / intervals.toFloat())
    val step = 10.0.pow(floor(log10(rawInterval)))
    val error = step * intervals / rawRange
    val niceInterval = when {
      error < 1.5 -> 1.0
      error < 3.0 -> 2.0
      error < 7.0 -> 5.0
      else -> 10.0
    } * step
    yFloor = floor(yMin / niceInterval) * niceInterval
    val baseInterval = (yMax - yFloor) / intervals
    intervalValue = ceil(baseInterval / step) * step
    yCeil = yFloor + (intervalValue * intervals)
    if (yCeil > yMax + intervalValue) {
      yCeil -= intervalValue
      yFloor -= intervalValue
    }
    range = yCeil - yFloor
  }

  fun valueAtInterval(interval: Int): Double {
    return yFloor + (intervalValue * interval)
  }

  /**
   * Return the [DataPoint] closest to [Offset.x] within [canvasWidth] or null if not found.
   */
  fun pointFrom(
    offset: Offset,
    canvasWidth: Float,
  ): DataPoint? {
    if (offset == Offset.Unspecified) return null
    val xOffset = offset.x - pathSize
    return when {
      xOffset <= 0 -> data.firstOrNull()
      xOffset >= canvasWidth -> data.lastOrNull()
      else -> {
        val pointIndex = (xOffset / (canvasWidth / data.size)).toInt()
        data.getOrNull(pointIndex)
      }
    }
  }

  /**
   * Create a [Path] presenting the [data] within the bounds of
   * the [canvasWidth] and [canvasHeight].
   *
   * @param canvasWidth The width to evenly distribute data points across.
   * @param canvasHeight The height to distribute data points in.
   * @param stopAtDataPoint The data point at which the [Path] should terminate.
   */
  fun createLinePath(
    path: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    stopAtDataPoint: DataPoint? = null,
  ): Path {
    path.rewind()
    // Use the pathSize as the base offset to ensure drawing
    // a Stroke with the path does not draw outside the parent
    val baseOffset = pathSize
    val stopAtIndex = stopAtDataPoint?.let { data.indexOf(it) }
    val normalizedData = data.map { point ->
      canvasHeight - ((point.y - yFloor) / range * canvasHeight).toFloat()
    }

    if (data.size == 1) {
      path.moveTo(baseOffset, normalizedData[0])
      path.lineTo(baseOffset + 1, normalizedData[0])
      return path
    }

    path.moveTo(baseOffset, normalizedData[0])
    val scaleX = canvasWidth / data.size
    // Start at `i = 1` and make lines from `i - 1` to `i`.
    // use `..` to include the last point in the loop.
    for (targetIndex in 1..normalizedData.lastIndex) {
      val startIndex = targetIndex - 1
      if (startIndex == stopAtIndex) {
        if (stopAtIndex == 0) {
          // if we stop drawing before creating a line,
          // add a small line to allow UI anchoring.
          path.lineTo(baseOffset + 1, normalizedData[0])
        }
        return path
      }

      val startPointX = baseOffset + (startIndex * scaleX)
      val startPointY = normalizedData[startIndex]
      val targetPointX = baseOffset + (targetIndex * scaleX)
      val targetPointY = normalizedData[targetIndex]
      path.quadraticTo(
        x1 = startPointX,
        y1 = startPointY,
        x2 = targetPointX,
        y2 = targetPointY
      )
    }

    return path
  }
}
