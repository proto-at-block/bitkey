package build.wallet.pricechart.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import build.wallet.time.truncateTo
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val yAxisPathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 2f)

private class WrappedPath(
  val path: Path,
)

/**
 * An interactive chart for currency prices and balance value history.
 *
 * @param dataPoints The list of data points to display.
 * @param onPointSelected Callback for when the user selects a point in the chart.
 * @param colorPrimary The primary color used to paint the chart lines.
 * @param formatYLabel Callback to format the given Y axis value for the label.
 * @param initialSelectedPoint An initially selected point in the graph, only useful for previews.
 * @param sparkLineMode When true, disables data labels and chart visual effects.
 */
@Composable
@Suppress("detekt:CyclomaticComplexMethod")
fun PriceChart(
  dataPoints: ImmutableList<DataPoint>,
  onPointSelected: (DataPoint?) -> Unit = {},
  colorPrimary: Color = WalletTheme.colors.bitcoinPrimary,
  colorSparkLine: Color = WalletTheme.colors.primaryForeground30,
  formatYLabel: (Double) -> String = { it.toString() },
  extractSecondaryYValue: ((DataPoint) -> Double)? = null,
  yAxisIntervals: Int = 10,
  initialSelectedPoint: DataPoint? = null,
  sparkLineMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val updatedFormatYLabel by rememberUpdatedState(formatYLabel)
  val updatedDataPoints by rememberUpdatedState(dataPoints)

  var inputHoverOffset by remember { mutableStateOf(Offset.Unspecified) }
  val density by rememberUpdatedState(LocalDensity.current)
  val pathSize = remember(density, sparkLineMode) {
    with(density) {
      (if (sparkLineMode) 3.0 else 4.0).dp.toPx()
    }
  }

  // The vertical chart intervals for y-axis labels and lines
  val chartDataState by remember {
    derivedStateOf {
      ChartDataState(
        data = updatedDataPoints,
        intervals = yAxisIntervals,
        pathSize = pathSize
      )
    }
  }
  // The pre-measured y-axis labels for each interval
  val textMeasurer = rememberTextMeasurer()
  val labelTextResults by remember {
    derivedStateOf {
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
  }

  BoxWithConstraints(
    modifier = modifier
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
  ) {
    val density = LocalDensity.current
    val secondaryYHeight = remember(extractSecondaryYValue) {
      if (extractSecondaryYValue == null) 0.dp else 60.dp
    }
    // The canvas height as pixels for Canvas drawing
    val canvasWidth by remember {
      derivedStateOf { with(density) { maxWidth.toPx() } }
    }
    val canvasHeight by remember {
      derivedStateOf { with(density) { (maxHeight - secondaryYHeight).toPx() } }
    }
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
      onPointSelected(value)
    }
    val animatedSelectedStateAlpha by animateFloatAsState(
      targetValue = if (selectedPoint == null) 0f else 1f,
      animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    )
    val chartElementColor = WalletTheme.colors.chartElement
    val chartPriceColor = WalletTheme.colors.foreground60
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
    val isPreview = LocalIsPreviewTheme.current
    var backgroundPath by remember {
      val initialPath = if (isPreview) {
        chartDataState.createLinePath(
          path = Path(),
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = canvasHeight
        )
      } else {
        Path()
      }
      mutableStateOf(WrappedPath(initialPath))
    }
    LaunchedEffect(chartDataState, adjustedCanvasWidth) {
      val path = backgroundPath.path.copy()
      withContext(Dispatchers.Default) {
        chartDataState.createLinePath(
          path = path,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = canvasHeight
        )
      }
      if (sparkLineMode && updatedDataPoints.isNotEmpty()) {
        lineEndOffset = sparklinePathMeasurer.run {
          setPath(path, false)
          getPosition(length)
        }
      }
      backgroundPath = WrappedPath(path)
    }
    // foreground line path used to preserve the primary color during selection
    var foregroundPath by remember {
      val initialPath = if (isPreview) {
        chartDataState.createLinePath(
          path = Path(),
          stopAtDataPoint = selectedPoint ?: previousSelectedPoint,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = canvasHeight
        )
      } else {
        Path()
      }
      mutableStateOf(WrappedPath(initialPath))
    }
    LaunchedEffect(dataPoints, selectedPoint, previousSelectedPoint) {
      val targetPoint = selectedPoint ?: previousSelectedPoint
      val path = foregroundPath.path.copy()
      withContext(Dispatchers.Default) {
        chartDataState.createLinePath(
          path = path,
          stopAtDataPoint = targetPoint,
          canvasWidth = adjustedCanvasWidth,
          canvasHeight = canvasHeight
        )
      }
      lineSplitOffset = activePathMeasurer.run {
        setPath(path, false)
        getPosition(length)
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
    val sparkThumbShadowBrush by remember {
      derivedStateOf {
        Brush.radialGradient(
          colors = listOf(
            colorPrimary.copy(alpha = 0.2f),
            Color.Transparent
          ),
          center = lineEndOffset ?: Offset.Zero,
          radius = with(density) { 20.dp.toPx() }
        )
      }
    }
    val verticalIndicatorBrush by remember {
      derivedStateOf {
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
    }

    val priceLineStroke = remember(pathSize) {
      Stroke(
        width = pathSize,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    }

    Spacer(
      modifier = Modifier
        .then(
          with(density) {
            Modifier.size(
              width = canvasWidth.toDp(),
              height = canvasHeight.toDp()
            )
          }
        )
        .drawBehind {
          if (!sparkLineMode) {
            for (i in 0..yAxisIntervals) {
              val labelY = (size.height - ((size.height / yAxisIntervals) * i))

              // y-axis interval labels
              labelTextResults.getOrNull(i)?.let { textLayoutResult ->
                drawText(
                  textLayoutResult = textLayoutResult,
                  color = chartPriceColor,
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

    val activeSecondaryValueColor = WalletTheme.colors.chartElement
    val inactiveSecondaryValueColor = WalletTheme.colors.stepperIncomplete
    Spacer(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .height(secondaryYHeight)
        .drawWithCache {
          if (extractSecondaryYValue == null) {
            return@drawWithCache onDrawBehind { }
          }
          val rangeSize = (dataPoints.last().x - dataPoints.first().x).seconds.inWholeDays
          val rangeInterval = when {
            rangeSize > 30 -> 30.days
            rangeSize > 7L -> 7.days
            rangeSize == 7L -> 1.days
            else -> 2.hours
          }
          val chunksMap = dataPoints
            .groupBy { Instant.fromEpochSeconds(it.x).truncateTo(rangeInterval) }
            .mapValues { (_, points) -> points.maxOf(extractSecondaryYValue) }
          val chunks = chunksMap.toList().dropLast(1)
          val selectedIndex = selectedPoint?.run {
            val key = Instant.fromEpochSeconds(x).truncateTo(rangeInterval)
            chunksMap.keys.indexOf(key).coerceAtMost(chunks.size)
          }
          val padding = 4f
          val width = (adjustedCanvasWidth - (padding * chunks.size)) / chunks.size
          val maxY = dataPoints.maxOf(extractSecondaryYValue)
          onDrawBehind {
            chunks.forEachIndexed { index, (_, value) ->
              val height = (value / maxY) * size.height
              drawRoundRect(
                color = if (index == selectedIndex) {
                  activeSecondaryValueColor
                } else {
                  inactiveSecondaryValueColor
                },
                cornerRadius = CornerRadius(10f, 10f),
                size = Size(width, height.toFloat()),
                topLeft = Offset(
                  x = (padding * 2) + (index * (width + padding)),
                  y = padding + (size.height - height).toFloat()
                )
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
