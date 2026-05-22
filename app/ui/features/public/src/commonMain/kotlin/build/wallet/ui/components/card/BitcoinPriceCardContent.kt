package build.wallet.ui.components.card

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.*
import build.wallet.pricechart.ChartRange
import build.wallet.pricechart.ui.PriceChart
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.BitcoinPrice
import build.wallet.ui.components.label.AnimatedAmount
import build.wallet.ui.components.label.AnimatedAmountAutoResizedLabel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.PI
import kotlin.math.sin

private const val SPARKLINE_PLACEHOLDER_POINT_COUNT = 24
private const val SPARKLINE_PLACEHOLDER_CYCLES = 1.75
private const val SPARKLINE_PLACEHOLDER_AMPLITUDE_FRACTION = 0.16f

@Composable
internal fun BitcoinPriceContent(model: BitcoinPrice) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // bitcoin price + value change
      PriceAndValueChangeColumn(model = model)

      Spacer(modifier = Modifier.weight(0.1f, fill = false))

      // price chart
      BitcoinPriceSparkline(
        model = model,
        modifier = Modifier
          .weight(0.27f, fill = false)
          .height(60.dp)
          .padding(end = 6.dp)
      )
    }
  }
}

@Composable
private fun BitcoinPriceSparkline(
  model: BitcoinPrice,
  modifier: Modifier = Modifier,
) {
  val showSparklinePlaceholder = model.data.isEmpty() || model.isLoading
  val placeholderSparklineColor = WalletTheme.colors.foreground10
  val placeholderAlpha by animateFloatAsState(
    targetValue = if (showSparklinePlaceholder) 1f else 0f,
    animationSpec = tween(durationMillis = 220),
    label = "sparkline-placeholder-alpha"
  )
  val sparklineAlpha by animateFloatAsState(
    targetValue = if (showSparklinePlaceholder || model.data.isEmpty()) 0f else 1f,
    animationSpec = tween(durationMillis = 220),
    label = "sparkline-alpha"
  )
  val placeholderPhase = rememberSparklinePlaceholderPhase(
    enabled = showSparklinePlaceholder || placeholderAlpha > 0.001f
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
  ) {
    if (placeholderAlpha > 0.001f) {
      SparklinePlaceholder(
        phase = placeholderPhase,
        color = placeholderSparklineColor,
        modifier = Modifier
          .fillMaxSize()
          .alpha(placeholderAlpha)
      )
    }

    if (model.data.isNotEmpty()) {
      PriceChart(
        dataPoints = model.data,
        range = ChartRange.DAY,
        animateDataTransition = !showSparklinePlaceholder,
        colorSparkLine = WalletTheme.colors.foreground,
        sparkLineMode = true,
        showSparkLineEndPoint = false,
        lineCornerRadius = 12.dp,
        yAxisIntervals = 10,
        modifier = Modifier
          .fillMaxSize()
          .alpha(sparklineAlpha)
      )
    }
  }
}

@Composable
private fun rememberSparklinePlaceholderPhase(enabled: Boolean): Float {
  if (!enabled) {
    return 0f
  }

  return rememberInfiniteTransition(label = "sparkline-placeholder")
    .animateFloat(
      initialValue = 0f,
      targetValue = (2 * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(
          durationMillis = 7200,
          easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
      ),
      label = "sparkline-placeholder-phase"
    )
    .value
}

@Composable
private fun SparklinePlaceholder(
  phase: Float,
  color: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  val path = remember { Path() }

  Canvas(modifier = modifier) {
    if (size.width <= 0f || size.height <= 0f) return@Canvas

    val points = List(SPARKLINE_PLACEHOLDER_POINT_COUNT) { index ->
      val progress = index.toFloat() / (SPARKLINE_PLACEHOLDER_POINT_COUNT - 1)
      val angle = (progress * SPARKLINE_PLACEHOLDER_CYCLES * 2 * PI) + phase
      val x = progress * size.width
      val y = (size.height / 2f) -
        (sin(angle).toFloat() * size.height * SPARKLINE_PLACEHOLDER_AMPLITUDE_FRACTION)
      x to y
    }

    path.rewind()
    val firstPoint = points.first()
    path.moveTo(firstPoint.first, firstPoint.second)

    for (targetIndex in 1 until points.lastIndex) {
      val startPoint = points[targetIndex - 1]
      val targetPoint = points[targetIndex]
      path.quadraticTo(
        x1 = startPoint.first,
        y1 = startPoint.second,
        x2 = (startPoint.first + targetPoint.first) / 2f,
        y2 = (startPoint.second + targetPoint.second) / 2f
      )
    }

    val penultimatePoint = points[points.lastIndex - 1]
    val finalPoint = points.last()
    path.quadraticTo(
      x1 = penultimatePoint.first,
      y1 = penultimatePoint.second,
      x2 = finalPoint.first,
      y2 = finalPoint.second
    )

    drawPath(
      path = path,
      color = color,
      style = androidx.compose.ui.graphics.drawscope.Stroke(
        width = 3.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }
}

@Composable
private fun PriceAndValueChangeColumn(
  model: BitcoinPrice,
) {
  Column(
    modifier = Modifier
      .wrapContentSize(),
    verticalArrangement = Arrangement.Bottom
  ) {
    Label(
      text = stringResource(Res.string.bitcoin_price_card_title),
      type = LabelType.Body3Mono,
      treatment = LabelTreatment.Secondary
    )

    val priceLabelType = LabelType.Body1Regular

    Box(
      modifier = Modifier
        .loadingScrim(model.isLoading),
      contentAlignment = Alignment.BottomStart
    ) {
      MeasureWithoutPlacement {
        Label(
          model = LabelModel.StringModel("$000,000.00"),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          maxLines = 1
        )
      }

      if (model.priceValue != null) {
        AnimatedAmountAutoResizedLabel(
          amount = AnimatedAmount(
            text = model.price,
            value = model.priceValue,
            animationKey = model.priceAnimationKey
          ),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          animate = true,
          animationLabel = "BitcoinPriceCardPrice",
          minTextSize = bitcoinPriceCardMinTextSize()
        )
      } else {
        Label(
          model = LabelModel.StringModel(model.price),
          type = priceLabelType,
          treatment = LabelTreatment.Primary,
          maxLines = 1
        )
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .loadingScrim(model.isLoading)
    ) {
      val animatedPriceDirection by animateFloatAsState(
        label = "price-direction",
        targetValue = model.priceDirection.orientation,
        animationSpec = tween(200)
      )
      Image(
        imageVector = vectorResource(Res.drawable.small_icon_arrow_up),
        contentDescription = null,
        colorFilter = ColorFilter.tint(WalletTheme.colors.foreground60),
        modifier = Modifier
          .size(16.dp)
          .rotate(animatedPriceDirection)
      )

      Box(
        contentAlignment = Alignment.BottomStart
      ) {
        MeasureWithoutPlacement {
          // size the loader based on the expected value size, not displayed to user
          Label(
            model = LabelModel.StringModel("50.00% Today"),
            type = LabelType.Body3Regular,
            treatment = LabelTreatment.Secondary,
            maxLines = 1
          )
        }
        Label(
          model = LabelModel.StringModel(model.priceChange),
          type = LabelType.Body3Regular,
          treatment = LabelTreatment.Secondary,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun bitcoinPriceCardMinTextSize(): TextUnit {
  return WalletTheme.labelStyle(
    type = LabelType.Body2Regular,
    treatment = LabelTreatment.Primary
  ).fontSize
}
