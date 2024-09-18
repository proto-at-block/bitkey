package build.wallet.pricechart.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.WalletTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * @param colorPrimary The primary color used to paint the chart lines.
 * @param formatYLabel Callback to format the given Y axis value for the label.
 * @param initialSelectedPoint An initially selected point in the graph, only useful for previews.
 * @param sparkLineMode When true, disables data labels and chart visual effects.
 */
@Composable
@Suppress("detekt:CyclomaticComplexMethod")
fun PriceChart(
  dataPoints: ImmutableList<DataPoint>,
  onPointSelected: (DataPoint) -> Unit = {},
  onPointDeselected: () -> Unit = {},
  colorPrimary: Color = WalletTheme.colors.bitcoinPrimary,
  colorSparkLine: Color = WalletTheme.colors.primaryForeground30,
  formatYLabel: (Double) -> String = { it.toString() },
  yAxisIntervals: Int = 10,
  initialSelectedPoint: DataPoint? = null,
  sparkLineMode: Boolean = false,
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
  val labelTextResults = remember(yAxisIntervals, chartDataState, sparkLineMode) {
    val labelCount = if (sparkLineMode) 0 else yAxisIntervals
    val textStyle = TextStyle.Default.copy(fontSize = 12.sp)
    List(labelCount) { i ->
      if (i % 2 == 1) {
        val labelValue = chartDataState.valueAtInterval(i)
        textMeasurer.measure(
          updatedFormatYLabel(labelValue),
          style = textStyle
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
    val backgroundPathColorTarget by remember {
      derivedStateOf {
        when {
          sparkLineMode -> colorSparkLine
          selectedPoint == null -> colorPrimary
          else -> chartElementColor
        }
      }
    }
    val backgroundPathColor by animateColorAsState(
      targetValue = backgroundPathColorTarget,
      animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    )
    val activePathMeasurer = remember { PathMeasure() }
    val sparklinePathMeasurer = remember { PathMeasure() }
    // the point where the active line becomes inactive, used to anchor selection ui
    var lineSplitOffset by remember { mutableStateOf(Offset.Unspecified) }
    var lineEndOffset by remember { mutableStateOf<Offset?>(null) }
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
      withContext(Dispatchers.Default) {
        chartDataState.createLinePath(
          path = path,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = constraints.maxHeight.toFloat()
        )
        if (sparkLineMode && updatedDataPoints.isNotEmpty()) {
          lineEndOffset = sparklinePathMeasurer.run {
            setPath(path, false)
            getPosition(length)
          }
        }
      }
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
    LaunchedEffect(dataPoints, selectedPoint, previousSelectedPoint) {
      val targetPoint = selectedPoint ?: previousSelectedPoint
      val path = foregroundPath.path
      withContext(Dispatchers.Default) {
        chartDataState.createLinePath(
          path = path,
          stopAtDataPoint = targetPoint,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = constraints.maxHeight.toFloat()
        )
        lineSplitOffset = activePathMeasurer.run {
          setPath(path, false)
          getPosition(length)
        }
      }
      foregroundPath = WrappedPath(path)
    }

    val thumbScale by animateFloatAsState(
      targetValue = if (selectedPoint == null) 0.5f else 1f
    )
    val thumbShadowBrush by remember {
      derivedStateOf {
        Brush.radialGradient(
          colors = listOf(
            colorPrimary.copy(alpha = 0.2f),
            Color.Transparent
          ),
          center = lineSplitOffset,
          radius = with(density) { thumbScale * (24.dp.toPx()) }
        )
      }
    }
    val sparkThumbShadowBrush = remember(colorPrimary, lineEndOffset, density) {
      Brush.radialGradient(
        colors = listOf(
          colorPrimary.copy(alpha = 0.2f),
          Color.Transparent
        ),
        center = lineEndOffset ?: Offset.Zero,
        radius = with(density) { 20.dp.toPx() }
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

    val priceLineStroke = remember(sparkLineMode) {
      Stroke(
        width = with(density) {
          (if (sparkLineMode) 3.0 else 4.0).dp.toPx()
        },
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
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
        .thenIf(!sparkLineMode) {
          Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                inputHoverOffset = if (change.pressed) change.position else Offset.Unspecified

                change.consume()
              }
            }
          }
        }
        .drawBehind {
          if (!sparkLineMode) {
            for (i in 0..yAxisIntervals) {
              val labelY = (size.height - ((size.height / yAxisIntervals) * i))

              // y-axis interval labels
              labelTextResults.getOrNull(i)?.let { textLayoutResult ->
                drawText(
                  textLayoutResult = textLayoutResult,
                  color = Color.Black.copy(alpha = 0.3f),
                  topLeft = Offset(
                    x = canvasWidth - textLayoutResult.size.width,
                    y = labelY - textLayoutResult.size.height - 6
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

          if (!sparkLineMode) {
            // foreground line path
            drawPath(
              path = foregroundPath.path,
              color = colorPrimary,
              style = priceLineStroke
            )
          }

          val sparkThumbOffset = lineEndOffset
          if (sparkLineMode && sparkThumbOffset != null) {
            drawCircle(
              center = sparkThumbOffset,
              brush = sparkThumbShadowBrush
            )
            // thumb indicator background
            drawCircle(
              color = colorPrimary,
              center = sparkThumbOffset,
              radius = 4.dp.toPx()
            )

            // thumb indicator foreground
            drawCircle(
              color = Color.White,
              center = sparkThumbOffset,
              radius = 2.dp.toPx()
            )
          }

          if (lineSplitOffset != Offset.Unspecified) {
            // thumb shadow
            drawCircle(
              center = lineSplitOffset,
              alpha = animatedSelectedStateAlpha,
              brush = thumbShadowBrush
            )

            // thumb indicator background
            drawCircle(
              color = Color.White,
              center = lineSplitOffset,
              alpha = animatedSelectedStateAlpha,
              radius = thumbScale * (8.dp.toPx())
            )

            // thumb indicator foreground
            drawCircle(
              color = colorPrimary,
              center = lineSplitOffset,
              alpha = animatedSelectedStateAlpha,
              radius = thumbScale * (6.dp.toPx())
            )
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
