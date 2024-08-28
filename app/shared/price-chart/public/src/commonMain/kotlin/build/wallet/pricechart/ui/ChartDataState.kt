package build.wallet.pricechart.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
) {
  private val yMin: Double = data.minOfOrNull { it.second } ?: 0.0
  private val yMax: Double = data.maxOfOrNull { it.second } ?: 1.0
  private val intervalValue: Double
  private val yFloor: Double
  private val yCeil: Double
  private val range: Double

  init {
    val rawRange = yMax - yMin
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

    val initialYCeil = ceil(yMax / niceInterval) * niceInterval
    val remainder = initialYCeil % step
    yCeil = if (remainder == 0.0) {
      initialYCeil
    } else {
      initialYCeil + (step - remainder)
    }
    val baseInterval = (yCeil - yFloor) / intervals
    intervalValue = ceil(baseInterval / step) * step
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
    return when {
      offset == Offset.Unspecified -> null
      offset.x <= 0 -> data.firstOrNull()
      offset.x >= canvasWidth -> data.lastOrNull()
      else -> {
        val pointIndex = (offset.x / (canvasWidth / data.size)).toInt()
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
    val stopAtIndex = stopAtDataPoint?.let { data.indexOf(it) }
    val normalizedData = data.map { (it.second - yFloor) / range }
    val xInterval = canvasWidth / normalizedData.lastIndex

    fun calculateY(i: Int) = (canvasHeight - normalizedData[i] * canvasHeight).toFloat()

    path.moveTo(0f, calculateY(0))

    for (index in 1 until normalizedData.lastIndex) {
      val startIndex = index - 1
      if (startIndex == stopAtIndex) {
        return path
      }

      val startPointX = startIndex * xInterval
      val startPointY = calculateY(startIndex)
      val targetPointX = index * xInterval
      val targetPointY = calculateY(index)
      path.quadraticBezierTo(
        x1 = startPointX,
        y1 = startPointY,
        x2 = (startPointX + targetPointX) / 2,
        y2 = (startPointY + targetPointY) / 2
      )
    }

    path.lineTo(canvasWidth, calculateY(normalizedData.lastIndex))

    return path
  }
}
