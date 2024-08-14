package build.wallet.pricechart.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.wallet.pricechart.DataPoint
import build.wallet.ui.theme.WalletTheme
import kotlinx.collections.immutable.ImmutableList

private val priceLineStroke = Stroke(
  width = 8f,
  cap = StrokeCap.Round,
  join = StrokeJoin.Round
)
private val yAxisPathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 2f)

private class WrappedPath(
  val path: Path,
)

/**
 * An interactive chart for currency prices and balance value history.
 *
 * @param dataPoints The list of data points to display.
 * @param onPointSelected Callback for when the user selects a point in the chart.
 * @param onPointDeselected Callback for when the user stops interacting with the chart.
 * @param primaryColor The primary color used to paint the chart lines.
 * @param formatYLabel Callback to format the given Y axis value for the label.
 * @param initialSelectedPoint An initially selected point in the graph, only useful for previews.
 */
@Composable
@Suppress("detekt:CyclomaticComplexMethod")
internal fun PriceChart(
  dataPoints: ImmutableList<DataPoint>,
  onPointSelected: (DataPoint) -> Unit = {},
  onPointDeselected: () -> Unit = {},
  primaryColor: Color = WalletTheme.colors.bitcoinPrimary,
  formatYLabel: (Double) -> String = { it.toString() },
  yAxisIntervals: Int = 7,
  initialSelectedPoint: DataPoint? = null,
  modifier: Modifier = Modifier,
) {
  val updatedFormatYLabel by rememberUpdatedState(formatYLabel)
  val updatedDataPoints by rememberUpdatedState(dataPoints)

  var inputHoverOffset by remember { mutableStateOf(Offset.Unspecified) }

  // The vertical chart intervals for y-axis labels and lines
  val chartDataState by remember {
    derivedStateOf {
      ChartDataState(
        data = updatedDataPoints,
        intervals = yAxisIntervals
      )
    }
  }
  // The pre-measured y-axis labels for each interval
  val textMeasurer = rememberTextMeasurer()
  val labelTextResults = remember(yAxisIntervals, chartDataState) {
    List(yAxisIntervals) { i ->
      if (i % 2 == 0) {
        val labelValue = chartDataState.yFloor + (chartDataState.intervalValue * i)
        textMeasurer.measure(
          updatedFormatYLabel(labelValue),
          style = TextStyle.Default.copy(fontSize = 11.sp)
        )
      } else {
        null
      }
    }
  }

  BoxWithConstraints(modifier = modifier) {
    val density = LocalDensity.current
    // The canvas height as pixels for Canvas drawing
    val canvasWidth = remember(density) { with(density) { maxWidth.toPx() } }
    val canvasHeight = remember(density) { with(density) { maxHeight.toPx() } }
    // canvas width trimmed by the label width + some padding
    val adjustedCanvasWidth by remember {
      derivedStateOf { labelTextResults.offsetWidth(canvasWidth) }
    }

    // retain the previous selection to provide line color animation during state change
    var previousSelectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    val selectedPoint by produceState(initialSelectedPoint, inputHoverOffset) {
      // store the previous selection
      value?.let { previousSelectedPoint = it }
      // if actively hovering, find the closest datapoint
      value = chartDataState.pointFrom(inputHoverOffset, adjustedCanvasWidth)
      // emit the selection change
      value?.run(onPointSelected) ?: onPointDeselected()
    }
    val animatedSelectedStateAlpha by animateFloatAsState(
      targetValue = if (selectedPoint == null) 0f else 1f,
      animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    )
    val chartElementColor = WalletTheme.colors.chartElement
    val backgroundPathColor by animateColorAsState(
      targetValue = if (selectedPoint == null) primaryColor else chartElementColor,
      animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    )
    // background line path for inactive line and color animation after deselection
    var backgroundPath by remember {
      val path = chartDataState.createLinePath(
        path = Path(),
        canvasWidth = adjustedCanvasWidth,
        canvasHeight = constraints.maxHeight.toFloat()
      )
      mutableStateOf(WrappedPath(path))
    }
    LaunchedEffect(chartDataState, adjustedCanvasWidth) {
      val path = backgroundPath.path
      chartDataState.createLinePath(
        path = path,
        canvasWidth = adjustedCanvasWidth,
        canvasHeight = constraints.maxHeight.toFloat()
      )
      backgroundPath = WrappedPath(path)
    }
    // foreground line path used to preserve the primary color during selection
    var foregroundPath by remember {
      val path = chartDataState.createLinePath(
        path = Path(),
        stopAtDataPoint = selectedPoint ?: previousSelectedPoint,
        canvasWidth = adjustedCanvasWidth,
        canvasHeight = constraints.maxHeight.toFloat()
      )
      mutableStateOf(WrappedPath(path))
    }
    LaunchedEffect(inputHoverOffset, previousSelectedPoint) {
      val targetPoint = selectedPoint ?: previousSelectedPoint
      val path = foregroundPath.path
      if (targetPoint != null) {
        chartDataState.createLinePath(
          path = path,
          stopAtDataPoint = targetPoint,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = constraints.maxHeight.toFloat()
        )
      }
      foregroundPath = WrappedPath(path)
    }
    // The offset for drawing line select effects
    val pathMeasurer = remember { PathMeasure() }
    val lineSplitOffset by remember {
      derivedStateOf {
        pathMeasurer.run {
          // The foreground line will be empty when selecting the first
          // point, so use the background line start in this case
          if (selectedPoint == chartDataState.data.first()) {
            setPath(backgroundPath.path, false)
            getPosition(0f)
          } else {
            setPath(foregroundPath.path, false)
            getPosition(length)
          }
        }
      }
    }

    val shadowSize = remember(density) { with(density) { 42.dp.toPx() } }
    val thumbShadowBrush = remember(primaryColor, lineSplitOffset, shadowSize) {
      Brush.radialGradient(
        colors = listOf(
          primaryColor.copy(alpha = 0.2f),
          Color.Transparent
        ),
        center = lineSplitOffset,
        radius = shadowSize
      )
    }
    val verticalIndicatorBrush = remember(canvasWidth) {
      Brush.linearGradient(
        listOf(
          Color.Transparent,
          chartElementColor,
          chartElementColor
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, canvasHeight)
      )
    }

    Spacer(
      modifier = Modifier
        .then(
          with(density) {
            Modifier.size(
              width = constraints.maxWidth.toDp(),
              height = constraints.maxHeight.toDp()
            )
          }
        )
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull() ?: continue
              inputHoverOffset = if (change.pressed) change.position else Offset.Unspecified

              change.consume()
            }
          }
        }
        .drawWithCache {
          onDrawBehind {
            for (i in 0..yAxisIntervals) {
              val labelY = (size.height - ((size.height / yAxisIntervals) * i))

              // y-axis interval labels
              labelTextResults.getOrNull(i)?.let { textLayoutResult ->
                drawText(
                  textLayoutResult = textLayoutResult,
                  color = Color.Black.copy(alpha = 0.3f),
                  topLeft = Offset(
                    x = canvasWidth - textLayoutResult.size.width,
                    y = labelY - textLayoutResult.size.height
                  )
                )
              }

              // y-axis interval line
              drawLine(
                color = chartElementColor,
                start = Offset(0f, labelY),
                end = Offset(canvasWidth, labelY),
                pathEffect = yAxisPathEffect,
                strokeWidth = 1.dp.toPx()
              )
            }

            // vertical indicator (drawn in reverse for entrance animation)
            if (lineSplitOffset != Offset.Unspecified) {
              val animatedIndicatorHeight = size.height - (size.height * animatedSelectedStateAlpha)
              drawLine(
                brush = verticalIndicatorBrush,
                strokeWidth = 2.dp.toPx(),
                start = Offset(lineSplitOffset.x, size.height),
                end = Offset(lineSplitOffset.x, animatedIndicatorHeight)
              )
            }

            // background line path
            drawPath(
              path = backgroundPath.path,
              color = backgroundPathColor,
              style = priceLineStroke
            )
            // foreground line path
            drawPath(
              path = foregroundPath.path,
              color = primaryColor,
              style = priceLineStroke
            )

            if (lineSplitOffset != Offset.Unspecified) {
              // thumb shadow
              drawCircle(
                center = lineSplitOffset,
                radius = shadowSize,
                alpha = animatedSelectedStateAlpha,
                brush = thumbShadowBrush
              )

              // thumb indicator background
              drawCircle(
                color = Color.White,
                center = lineSplitOffset,
                alpha = animatedSelectedStateAlpha,
                radius = 8.dp.toPx()
              )

              // thumb indicator foreground
              drawCircle(
                color = primaryColor,
                center = lineSplitOffset,
                alpha = animatedSelectedStateAlpha,
                radius = 6.dp.toPx()
              )
            }
          }
        }
    )
  }
}

/**
 * Offset the [canvasWidth] by the max [TextLayoutResult.size] width including
 * some padding.
 *
 * Returns [canvasWidth] when list is empty or filled with null.
 */
private fun List<TextLayoutResult?>.offsetWidth(canvasWidth: Float) =
  mapNotNull { it?.size }
    .maxOfOrNull { it.width.toFloat() }
    ?.let { canvasWidth - (it * 1.5f) }
    ?: canvasWidth
