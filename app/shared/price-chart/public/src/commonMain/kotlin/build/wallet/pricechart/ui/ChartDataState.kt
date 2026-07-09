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
  val valuePaddingFraction: Float = 0f,
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
    val baseInterval = (yMax - yFloor) / intervals
    intervalValue = ceil(baseInterval / step) * step
    yCeil = yFloor + (intervalValue * intervals)
    if (yCeil > yMax + intervalValue) {
      yCeil -= intervalValue
      yFloor -= intervalValue
    }
    if (valuePaddingFraction > 0f) {
      val padding = (yCeil - yFloor) * valuePaddingFraction
      yFloor -= padding
      yCeil += padding
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
   * Returns the rendered chart position for the given [DataPoint], or null if it is not present.
   */
  fun pointOffset(
    point: DataPoint,
    canvasWidth: Float,
    canvasHeight: Float,
  ): Offset? {
    val index = data.indexOf(point).takeIf { it >= 0 } ?: return null
    val baseOffset = pathSize
    val scaleX = canvasWidth / data.size
    val normalizedY = canvasHeight - ((point.y - yFloor) / range * canvasHeight).toFloat()
    return Offset(
      x = baseOffset + (index * scaleX),
      y = normalizedY
    )
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
    val stopAtIndex = stopAtDataPoint
      ?.let { data.indexOf(it) }
      ?.takeIf { it >= 0 }
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
    val stopAtIndexInclusive = stopAtIndex ?: normalizedData.lastIndex
    if (stopAtIndexInclusive == 0) {
      path.lineTo(baseOffset + 1, normalizedData[0])
      return path
    }

    val points = normalizedData.mapIndexed { index, y ->
      Offset(
        x = baseOffset + (index * scaleX),
        y = y
      )
    }
    for (targetIndex in 1 until stopAtIndexInclusive) {
      val startPoint = points[targetIndex - 1]
      val targetPoint = points[targetIndex]
      val midPointX = (startPoint.x + targetPoint.x) / 2f
      val midPointY = (startPoint.y + targetPoint.y) / 2f
      path.quadraticTo(
        x1 = startPoint.x,
        y1 = startPoint.y,
        x2 = midPointX,
        y2 = midPointY
      )
    }

    val penultimatePoint = points[stopAtIndexInclusive - 1]
    val finalPoint = points[stopAtIndexInclusive]
    path.quadraticTo(
      x1 = penultimatePoint.x,
      y1 = penultimatePoint.y,
      x2 = finalPoint.x,
      y2 = finalPoint.y
    )

    return path
  }
}
