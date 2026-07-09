package build.wallet.pricechart.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.DataPoint
import build.wallet.time.truncateTo
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
 * @param onDisplayedPointSelected Callback for when the scrubber crosses a data point.
 * @param colorPrimary The primary color used to paint the chart lines.
 * @param formatYLabel Callback to format the given Y axis value for the label.
 * @param initialSelectedPoint An initially selected point in the graph, only useful for previews.
 * @param sparkLineMode When true, disables data labels and chart visual effects.
 * @param showSparkLineEndPoint Whether to show the dot at the end of the sparkline.
 * @param lineCornerRadius Corner radius used to smooth line bends.
 * @param animateDataTransition Whether to morph between old and new data sets.
 */
@Composable
@Suppress("detekt:CyclomaticComplexMethod")
fun PriceChart(
  dataPoints: ImmutableList<DataPoint>,
  range: ChartRange,
  onPointSelected: (DataPoint?) -> Unit = {},
  onDisplayedPointSelected: (DataPoint?) -> Unit = {},
  colorPrimary: Color = WalletTheme.colors.bitcoinPrimary,
  colorSparkLine: Color = WalletTheme.colors.primaryForeground30,
  formatYLabel: (Double, Boolean) -> String = { v, _ -> v.toString() },
  extractSecondaryYValue: ((DataPoint) -> Double)? = null,
  yAxisIntervals: Int = 10,
  initialSelectedPoint: DataPoint? = null,
  isInteractive: Boolean = true,
  sparkLineMode: Boolean = false,
  showSparkLineEndPoint: Boolean = true,
  lineCornerRadius: Dp = if (sparkLineMode) 6.dp else 0.dp,
  sparklineValuePaddingFraction: Float = 0f,
  animateDataTransition: Boolean = !sparkLineMode,
  modifier: Modifier = Modifier,
) {
  val shouldAnimateDataTransition = animateDataTransition
  val transitionProgress = remember { Animatable(1f) }
  var transitionStartData by remember { mutableStateOf(dataPoints) }
  var transitionEndData by remember { mutableStateOf(dataPoints) }
  var transitionStartValuePaddingFraction by remember {
    mutableFloatStateOf(sparklineValuePaddingFraction)
  }
  var transitionEndValuePaddingFraction by remember {
    mutableFloatStateOf(sparklineValuePaddingFraction)
  }
  val isMorphing by remember(shouldAnimateDataTransition) {
    derivedStateOf {
      shouldAnimateDataTransition && transitionProgress.value < 1f
    }
  }
  val isChartInteractive by remember(isInteractive, dataPoints) {
    derivedStateOf { isInteractive && !isMorphing }
  }
  val updatedFormatYLabel by rememberUpdatedState(formatYLabel)
  val updatedDataPoints by rememberUpdatedState(dataPoints)
  val updatedOnDisplayedPointSelected by rememberUpdatedState(onDisplayedPointSelected)

  LaunchedEffect(dataPoints, shouldAnimateDataTransition, sparklineValuePaddingFraction) {
    if (!shouldAnimateDataTransition || dataPoints.isEmpty()) {
      transitionStartData = dataPoints
      transitionEndData = dataPoints
      transitionStartValuePaddingFraction = sparklineValuePaddingFraction
      transitionEndValuePaddingFraction = sparklineValuePaddingFraction
      transitionProgress.snapTo(1f)
      return@LaunchedEffect
    }

    val currentDisplayedData = if (transitionProgress.value < 1f) {
      captureInterpolatedDataPoints(
        startData = transitionStartData,
        endData = transitionEndData,
        progress = transitionProgress.value
      )
    } else {
      transitionEndData
    }
    val currentDisplayedValuePaddingFraction = if (transitionProgress.value < 1f) {
      lerp(
        transitionStartValuePaddingFraction,
        transitionEndValuePaddingFraction,
        transitionProgress.value
      )
    } else {
      transitionEndValuePaddingFraction
    }

    if (transitionEndData.isEmpty()) {
      transitionStartData = dataPoints
      transitionEndData = dataPoints
      transitionStartValuePaddingFraction = sparklineValuePaddingFraction
      transitionEndValuePaddingFraction = sparklineValuePaddingFraction
      transitionProgress.snapTo(1f)
      return@LaunchedEffect
    }

    if (
      haveSamePoints(currentDisplayedData, dataPoints) &&
      abs(currentDisplayedValuePaddingFraction - sparklineValuePaddingFraction) < 0.001f
    ) {
      transitionStartData = dataPoints
      transitionEndData = dataPoints
      transitionStartValuePaddingFraction = sparklineValuePaddingFraction
      transitionEndValuePaddingFraction = sparklineValuePaddingFraction
      transitionProgress.snapTo(1f)
      return@LaunchedEffect
    }

    transitionStartData = currentDisplayedData
    transitionEndData = dataPoints
    transitionStartValuePaddingFraction = currentDisplayedValuePaddingFraction
    transitionEndValuePaddingFraction = sparklineValuePaddingFraction
    transitionProgress.snapTo(0f)
    transitionProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
      )
    )
  }

  var inputHoverOffset by remember { mutableStateOf(Offset.Unspecified) }
  val density by rememberUpdatedState(LocalDensity.current)
  val pathSize = remember(density, sparkLineMode) {
    with(density) {
      (if (sparkLineMode) 3.0 else 4.0).dp.toPx()
    }
  }

  // The vertical chart intervals for y-axis labels and lines
  val chartDataState by remember(
    dataPoints,
    yAxisIntervals,
    pathSize,
    range,
    sparklineValuePaddingFraction
  ) {
    derivedStateOf {
      ChartDataState(
        data = updatedDataPoints,
        intervals = yAxisIntervals,
        pathSize = pathSize,
        chartRange = range,
        valuePaddingFraction = sparklineValuePaddingFraction
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
            updatedFormatYLabel(labelValue, chartDataState.precise),
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
      .thenIf(!sparkLineMode && isChartInteractive) {
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
    val secondaryYHeight = remember(extractSecondaryYValue) {
      if (extractSecondaryYValue == null) 0.dp else 60.dp
    }
    // The canvas height as pixels for Canvas drawing
    val canvasWidth by remember {
      derivedStateOf { with(density) { floor(maxWidth.toPx()) } }
    }
    val canvasHeight by remember {
      derivedStateOf { with(density) { floor((maxHeight - secondaryYHeight).toPx()) } }
    }
    // canvas width trimmed by the label width + some padding
    val adjustedCanvasWidth by remember {
      derivedStateOf { floor(labelTextResults.offsetWidth(canvasWidth)) }
    }

    // retain the previous selection to provide line color animation during state change
    var previousSelectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    LaunchedEffect(isChartInteractive) {
      if (!isChartInteractive) {
        inputHoverOffset = Offset.Unspecified
        previousSelectedPoint = null
      }
    }
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
    val inactivePathColor = WalletTheme.colors.subtleBackground
    val backgroundPathColorTarget = when {
      sparkLineMode -> colorSparkLine
      selectedPoint == null -> colorPrimary
      else -> inactivePathColor
    }
    val backgroundPathColor by animateColorAsState(
      targetValue = backgroundPathColorTarget,
      animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
    )
    val activePathMeasurer = remember { PathMeasure() }
    val sparklinePathMeasurer = remember { PathMeasure() }
    val morphPath by remember(
      transitionStartData,
      transitionEndData,
      adjustedCanvasWidth,
      canvasHeight,
      pathSize,
      yAxisIntervals,
      range,
      transitionStartValuePaddingFraction,
      transitionEndValuePaddingFraction
    ) {
      derivedStateOf {
        if (!isMorphing || transitionStartData.isEmpty() || transitionEndData.isEmpty()) {
          null
        } else {
          createMorphPath(
            startData = transitionStartData,
            endData = transitionEndData,
            progress = if (transitionProgress.value < 1f) transitionProgress.value else 1f,
            canvasWidth = adjustedCanvasWidth,
            canvasHeight = canvasHeight,
            pathSize = pathSize,
            yAxisIntervals = yAxisIntervals,
            chartRange = range,
            startValuePaddingFraction = transitionStartValuePaddingFraction,
            endValuePaddingFraction = transitionEndValuePaddingFraction
          )
        }
      }
    }
    // the point where the active line becomes inactive, used to anchor selection ui
    var lineSplitOffsetTarget by remember { mutableStateOf(Offset.Unspecified) }
    var lastInteractiveLineSplitOffsetTarget by remember { mutableStateOf<Offset?>(null) }
    var lineSplitOffset by remember { mutableStateOf(Offset.Unspecified) }
    var previousScrubX by remember { mutableStateOf<Float?>(null) }
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
    // Previews and Paparazzi snapshots capture the first frame before effects
    // calculate lineSplitOffset, so keep the initial active segment available.
    val previewForegroundPath = remember(
      isPreview,
      chartDataState,
      selectedPoint,
      previousSelectedPoint,
      adjustedCanvasWidth,
      canvasHeight
    ) {
      if (isPreview) {
        WrappedPath(
          chartDataState.createLinePath(
            path = Path(),
            stopAtDataPoint = selectedPoint ?: previousSelectedPoint,
            canvasWidth = adjustedCanvasWidth,
            canvasHeight = canvasHeight
          )
        )
      } else {
        null
      }
    }
    val pointXPositions by remember(updatedDataPoints, chartDataState, adjustedCanvasWidth, canvasHeight) {
      derivedStateOf {
        updatedDataPoints.mapNotNull { point ->
          chartDataState.pointOffset(point, adjustedCanvasWidth, canvasHeight)
            ?.x
            ?.let { x -> point to x }
        }
      }
    }
    LaunchedEffect(
      dataPoints,
      selectedPoint,
      previousSelectedPoint,
      inputHoverOffset,
      backgroundPath,
      adjustedCanvasWidth,
      canvasHeight
    ) {
      val selectedPointOffset =
        (selectedPoint ?: previousSelectedPoint)
          ?.let { chartDataState.pointOffset(it, adjustedCanvasWidth, canvasHeight) }
          ?.let { targetOffset ->
            activePathMeasurer.run {
              setPath(backgroundPath.path, false)
              positionAtX(targetOffset.x)
            }
          }

      lineSplitOffsetTarget = when {
        inputHoverOffset != Offset.Unspecified -> {
          activePathMeasurer.run {
            setPath(backgroundPath.path, false)
            positionAtX(inputHoverOffset.x.coerceIn(pathSize, adjustedCanvasWidth))
          }.also { lastInteractiveLineSplitOffsetTarget = it }
        }
        else -> lastInteractiveLineSplitOffsetTarget ?: selectedPointOffset ?: Offset.Unspecified
      }
    }
    LaunchedEffect(
      lineSplitOffsetTarget,
      inputHoverOffset != Offset.Unspecified,
      pointXPositions
    ) {
      if (lineSplitOffsetTarget == Offset.Unspecified) {
        lineSplitOffset = Offset.Unspecified
        previousScrubX = null
        return@LaunchedEffect
      }

      lineSplitOffset = lineSplitOffsetTarget

      if (inputHoverOffset == Offset.Unspecified) {
        previousScrubX = null
        return@LaunchedEffect
      }

      previousScrubX?.let { previousX ->
        crossedPointsBetween(
          pointXPositions = pointXPositions,
          previousX = previousX,
          currentX = lineSplitOffsetTarget.x
        ).forEach(updatedOnDisplayedPointSelected)
      }

      previousScrubX = lineSplitOffsetTarget.x
    }

    val thumbScale by animateFloatAsState(
      targetValue = if (selectedPoint == null) 0.5f else 1f
    )
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

    val priceLineStroke = remember(pathSize, density, lineCornerRadius) {
      val lineCornerRadiusPx = with(density) { lineCornerRadius.toPx() }
      Stroke(
        width = pathSize,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = if (lineCornerRadiusPx > 0f) {
          PathEffect.cornerPathEffect(lineCornerRadiusPx)
        } else {
          null
        }
      )
    }

    val activeSecondaryValueColor = colorPrimary
    val inactiveSecondaryValueColor = WalletTheme.colors.subtleBackground
    Spacer(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .height(secondaryYHeight)
        .padding(top = 4.dp)
        .drawWithCache {
          if (extractSecondaryYValue == null) {
            return@drawWithCache onDrawBehind { }
          }
          val lastIndex = if (dataPoints.size > 2) {
            dataPoints.lastIndex - 1
          } else {
            dataPoints.lastIndex
          }
          val duration = (dataPoints[lastIndex].x - dataPoints.first().x).seconds
          val rangeInterval = when {
            duration < 25.hours -> 1.hours
            duration < 31.days -> 1.days
            duration < 91.days -> (duration.inWholeDays / 7).days
            duration < 366.days -> (duration.inWholeDays / 30).days
            else -> (duration.inWholeDays / 90).days
          }
          val chunks = dataPoints
            .groupBy { Instant.fromEpochSeconds(it.x).truncateTo(rangeInterval) }
            .mapValues { (_, points) -> extractSecondaryYValue(points.last()) }
            .toList()
          val padding = 4f
          val width = if (chunks.size == 1) {
            10.dp.toPx()
          } else {
            val chunkPaddingTotal = padding * (chunks.size - 1)
            (adjustedCanvasWidth - chunkPaddingTotal) / chunks.size
          }
          val maxY = dataPoints.maxOf(extractSecondaryYValue)
          val selectionOffset = lineSplitOffset.takeUnless {
            it == Offset.Unspecified || inputHoverOffset == Offset.Unspecified
          }
          onDrawBehind {
            chunks.forEachIndexed { index, (_, value) ->
              val adjustedCanvasHeight = size.height - padding
              val height = (value / maxY) * adjustedCanvasHeight
              val offset = Offset(
                x = if (chunks.size == 1) {
                  adjustedCanvasWidth - width - padding
                } else {
                  (padding * 2) + (index * (width + padding))
                },
                y = padding + (adjustedCanvasHeight - height).toFloat()
              )
              val isSelected = selectionOffset?.let { (x) ->
                val safeX = x.coerceIn(0f, adjustedCanvasWidth)
                val offsetX = offset.x - (padding / 2)
                safeX >= floor(offsetX) && safeX <= ceil(offsetX + width)
              }
              drawRoundRect(
                color = if (isSelected == true) {
                  activeSecondaryValueColor
                } else {
                  inactiveSecondaryValueColor
                },
                cornerRadius = CornerRadius(10f, 10f),
                size = Size(width, height.toFloat()),
                topLeft = offset
              )
            }
          }
        }
    )

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

          val currentMorphPath = morphPath
          if (currentMorphPath != null) {
            drawPath(
              path = currentMorphPath,
              color = if (sparkLineMode) backgroundPathColor else colorPrimary,
              style = priceLineStroke
            )
          } else {
            // background line path
            drawPath(
              path = backgroundPath.path,
              color = backgroundPathColor,
              style = priceLineStroke
            )

            if (!sparkLineMode) {
              // foreground line path
              if (lineSplitOffset != Offset.Unspecified) {
                clipRect(right = lineSplitOffset.x) {
                  drawPath(
                    path = backgroundPath.path,
                    color = colorPrimary,
                    style = priceLineStroke
                  )
                }
              } else {
                previewForegroundPath?.let { foregroundPath ->
                  drawPath(
                    path = foregroundPath.path,
                    color = colorPrimary,
                    style = priceLineStroke
                  )
                }
              }
            }
          }

          val sparkThumbOffset = lineEndOffset
          if (sparkLineMode && sparkThumbOffset != null && showSparkLineEndPoint) {
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

private fun PathMeasure.positionAtX(targetX: Float): Offset {
  if (length <= 0f) return Offset.Unspecified

  var low = 0f
  var high = length
  repeat(18) {
    val mid = (low + high) / 2f
    val midPosition = getPosition(mid)
    if (midPosition.x < targetX) {
      low = mid
    } else {
      high = mid
    }
  }

  val lowPosition = getPosition(low)
  val highPosition = getPosition(high)
  return if (abs(lowPosition.x - targetX) <= abs(highPosition.x - targetX)) {
    lowPosition
  } else {
    highPosition
  }
}

private fun crossedPointsBetween(
  pointXPositions: List<Pair<DataPoint, Float>>,
  previousX: Float,
  currentX: Float,
): List<DataPoint> {
  val epsilon = 0.5f
  return when {
    currentX > previousX -> {
      pointXPositions
        .filter { (_, pointX) -> pointX > previousX + epsilon && pointX <= currentX + epsilon }
        .map { (point, _) -> point }
    }
    currentX < previousX -> {
      pointXPositions
        .asReversed()
        .filter { (_, pointX) -> pointX < previousX - epsilon && pointX >= currentX - epsilon }
        .map { (point, _) -> point }
    }
    else -> emptyList()
  }
}

private fun captureInterpolatedDataPoints(
  startData: ImmutableList<DataPoint>,
  endData: ImmutableList<DataPoint>,
  progress: Float,
): ImmutableList<DataPoint> {
  if (startData.isEmpty() || endData.isEmpty()) return endData

  val clampedProgress = progress.coerceIn(0f, 1f)
  return kotlinx.collections.immutable.persistentListOf<DataPoint>().builder().apply {
    repeat(endData.size) { index ->
      val normalizedPosition = if (endData.lastIndex <= 0) {
        0f
      } else {
        index.toFloat() / endData.lastIndex
      }
      val startPoint = sampleDataPointAt(startData, normalizedPosition)
      val endPoint = endData[index]
      add(
        DataPoint(
          first = endPoint.x,
          second = lerp(startPoint.y, endPoint.y, clampedProgress.toDouble())
        )
      )
    }
  }.build()
}

private fun createMorphPath(
  startData: ImmutableList<DataPoint>,
  endData: ImmutableList<DataPoint>,
  progress: Float,
  canvasWidth: Float,
  canvasHeight: Float,
  pathSize: Float,
  yAxisIntervals: Int,
  chartRange: ChartRange,
  startValuePaddingFraction: Float,
  endValuePaddingFraction: Float,
): Path {
  val clampedProgress = progress.coerceIn(0f, 1f)
  val startChartState = ChartDataState(
    data = startData,
    intervals = yAxisIntervals,
    pathSize = pathSize,
    chartRange = chartRange,
    valuePaddingFraction = startValuePaddingFraction
  )
  val endChartState = ChartDataState(
    data = endData,
    intervals = yAxisIntervals,
    pathSize = pathSize,
    chartRange = chartRange,
    valuePaddingFraction = endValuePaddingFraction
  )
  val sampleCount = maxOf(startData.size, endData.size).coerceAtLeast(2)
  val blendedOffsets = List(sampleCount) { index ->
    val normalizedPosition = index.toFloat() / (sampleCount - 1)
    val startOffset = sampleOffsetAt(
      dataPoints = startData,
      chartDataState = startChartState,
      normalizedPosition = normalizedPosition,
      canvasWidth = canvasWidth,
      canvasHeight = canvasHeight
    )
    val endOffset = sampleOffsetAt(
      dataPoints = endData,
      chartDataState = endChartState,
      normalizedPosition = normalizedPosition,
      canvasWidth = canvasWidth,
      canvasHeight = canvasHeight
    )
    Offset(
      x = lerp(startOffset.x, endOffset.x, clampedProgress),
      y = lerp(startOffset.y, endOffset.y, clampedProgress)
    )
  }

  return createLinePathFromOffsets(
    points = blendedOffsets
  )
}

private fun sampleDataPointAt(
  dataPoints: ImmutableList<DataPoint>,
  normalizedPosition: Float,
): DataPoint {
  if (dataPoints.size == 1) return dataPoints.first()

  val position = normalizedPosition.coerceIn(0f, 1f) * dataPoints.lastIndex
  val lowerIndex = floor(position).toInt()
  val upperIndex = ceil(position).toInt()
  if (lowerIndex == upperIndex) {
    return dataPoints[lowerIndex]
  }

  val progress = position - lowerIndex
  val lowerPoint = dataPoints[lowerIndex]
  val upperPoint = dataPoints[upperIndex]
  return DataPoint(
    first = lowerPoint.x,
    second = lerp(lowerPoint.y, upperPoint.y, progress.toDouble())
  )
}

private fun sampleOffsetAt(
  dataPoints: ImmutableList<DataPoint>,
  chartDataState: ChartDataState,
  normalizedPosition: Float,
  canvasWidth: Float,
  canvasHeight: Float,
): Offset {
  if (dataPoints.size == 1) {
    return chartDataState.pointOffset(dataPoints.first(), canvasWidth, canvasHeight) ?: Offset.Zero
  }

  val position = normalizedPosition.coerceIn(0f, 1f) * dataPoints.lastIndex
  val lowerIndex = floor(position).toInt()
  val upperIndex = ceil(position).toInt()
  val lowerOffset = chartDataState.pointOffset(dataPoints[lowerIndex], canvasWidth, canvasHeight) ?: Offset.Zero
  if (lowerIndex == upperIndex) {
    return lowerOffset
  }

  val upperOffset = chartDataState.pointOffset(dataPoints[upperIndex], canvasWidth, canvasHeight) ?: lowerOffset
  val fraction = position - lowerIndex
  return Offset(
    x = lerp(lowerOffset.x, upperOffset.x, fraction),
    y = lerp(lowerOffset.y, upperOffset.y, fraction)
  )
}

private fun createLinePathFromOffsets(
  points: List<Offset>,
): Path {
  return Path().apply {
    if (points.isEmpty()) return@apply
    if (points.size == 1) {
      moveTo(points[0].x, points[0].y)
      lineTo(points[0].x + 1f, points[0].y)
      return@apply
    }

    moveTo(points[0].x, points[0].y)
    for (targetIndex in 1 until points.lastIndex) {
      val startPoint = points[targetIndex - 1]
      val targetPoint = points[targetIndex]
      val midPoint = Offset(
        x = (startPoint.x + targetPoint.x) / 2f,
        y = (startPoint.y + targetPoint.y) / 2f
      )
      quadraticTo(
        x1 = startPoint.x,
        y1 = startPoint.y,
        x2 = midPoint.x,
        y2 = midPoint.y
      )
    }
    val penultimatePoint = points[points.lastIndex - 1]
    val finalPoint = points.last()
    quadraticTo(
      x1 = penultimatePoint.x,
      y1 = penultimatePoint.y,
      x2 = finalPoint.x,
      y2 = finalPoint.y
    )
  }
}

private fun haveSamePoints(
  first: ImmutableList<DataPoint>,
  second: ImmutableList<DataPoint>,
): Boolean {
  if (first.size != second.size) return false
  return first.indices.all { index ->
    first[index].x == second[index].x && first[index].y == second[index].y
  }
}

private fun lerp(
  start: Double,
  stop: Double,
  fraction: Double,
): Double {
  return start + (stop - start) * fraction
}

private fun lerp(
  start: Float,
  stop: Float,
  fraction: Float,
): Float {
  return start + (stop - start) * fraction
}
