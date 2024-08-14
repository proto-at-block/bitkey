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
  val yMin: Double = data.minOfOrNull { it.second } ?: 0.0
  val yMax: Double = data.maxOfOrNull { it.second } ?: 1.0
  val intervalValue: Double
  val yFloor: Double
  val yCeil: Double

  init {
    val range = yMax - yMin
    val rawInterval = (range / intervals.toFloat())
    val step = 10.0.pow(floor(log10(rawInterval)))
    val error = step * intervals / range
    intervalValue = when {
      error <= 0.15 -> step * 10.0
      error <= 0.35 -> step * 5.0
      error <= 0.75 -> step * 2.0
      else -> step
    }.let { newInterval ->
      if (newInterval < rawInterval) {
        newInterval * 2
      } else {
        newInterval
      }
    }
    yFloor = (floor(yMin / intervalValue) * intervalValue).coerceAtLeast(0.0)
    yCeil = yFloor + (intervalValue * intervals)
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
    val normalizedData = data.map { (it.second - yFloor) / (yCeil - yFloor) }
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
