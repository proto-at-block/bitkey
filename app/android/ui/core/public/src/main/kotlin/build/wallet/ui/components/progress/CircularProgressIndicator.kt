package build.wallet.ui.components.progress

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.core.TimerDirection.Clockwise
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlin.time.Duration.Companion.seconds
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator

inline val Float.asDegreeAngle: Float get() = 360f / 1.0f * this

@Composable
fun CircularProgressIndicator(
  modifier: Modifier = Modifier,
  // TODO(W-8034): use Progress type.
  progress: Float,
  direction: TimerDirection,
  remainingSeconds: Long,
  size: Dp,
  indicatorColor: Color = WalletTheme.colors.primary,
  strokeWidth: Dp = size / 16.6f, // Stroke width defaults to proportional to the size.
) {
  /** Animate on initial composition from the start. */
  var progressValue by remember { mutableFloatStateOf(progress) }
  LaunchedEffect("start-progress-animation") {
    progressValue = 1f
  }

  val animatedProgress by animateFloatAsState(
    targetValue = progressValue,
    animationSpec =
      tween(
        durationMillis = remainingSeconds.seconds.inWholeMilliseconds.toInt(),
        easing = LinearEasing
      ),
    label = "progressAnimation"
  )

  CircularProgressIndicator(
    modifier = modifier,
    progress = animatedProgress,
    direction = direction,
    size = size,
    indicatorColor = indicatorColor,
    backgroundColor = WalletTheme.colors.foreground10,
    strokeWidth = strokeWidth,
    strokeCap = StrokeCap.Round
  )
}

/**
 * Implementation is largely based on [MaterialCircularProgressIndicator]
 */
@Composable
fun CircularProgressIndicator(
  modifier: Modifier = Modifier,
  // TODO(W-8034): use Progress type.
  progress: Float,
  direction: TimerDirection,
  size: Dp,
  indicatorColor: Color,
  backgroundColor: Color,
  strokeWidth: Dp,
  strokeCap: StrokeCap = StrokeCap.Round,
) {
  val stroke =
    with(LocalDensity.current) {
      Stroke(width = strokeWidth.toPx(), cap = strokeCap)
    }
  Canvas(
    modifier
      .progressSemantics(progress)
      .size(size)
  ) {
    // Start at 12 o'clock
    val startAngle = 270f
    val prog = if (direction == CounterClockwise) 1 - progress else progress
    // Full background circle.
    drawCircularIndicator(360F, 360F, backgroundColor, stroke)
    drawCircularIndicator(startAngle, prog, indicatorColor, stroke)
  }
}

private fun DrawScope.drawCircularIndicator(
  startAngle: Float,
  // TODO(W-8034): use Progress type.
  progress: Float,
  color: Color,
  stroke: Stroke,
) {
  // To draw this circle we need a rect with edges that line up with the midpoint of the stroke.
  // To do this we need to remove half the stroke width from the total diameter for both sides.
  val diameterOffset = stroke.width / 2
  val arcDimen = size.width - 2 * diameterOffset
  drawArc(
    color = color,
    startAngle = startAngle,
    sweepAngle = progress.asDegreeAngle,
    useCenter = false,
    topLeft = Offset(diameterOffset, diameterOffset),
    size = Size(arcDimen, arcDimen),
    style = stroke
  )
}

@Preview
@Composable
private fun CircularProgressIndicatorPreview() {
  PreviewWalletTheme {
    CircularProgressIndicator(
      progress = 0.10f,
      direction = Clockwise,
      size = 200.dp,
      remainingSeconds = 1000
    )
  }
}

@Preview
@Composable
private fun CircularProgressIndicatorCounterClockwisePreview() {
  PreviewWalletTheme {
    CircularProgressIndicator(
      progress = 0.10f,
      direction = CounterClockwise,
      size = 200.dp,
      remainingSeconds = 0
    )
  }
}

@Preview
@Composable
private fun CircularProgressDonePreview() {
  PreviewWalletTheme {
    CircularProgressIndicator(
      progress = 1.0f,
      direction = CounterClockwise,
      size = 200.dp,
      remainingSeconds = 0
    )
  }
}

@Preview
@Composable
private fun CircularProgressFullPreview() {
  PreviewWalletTheme {
    CircularProgressIndicator(
      progress = 0.0f,
      direction = CounterClockwise,
      size = 200.dp,
      remainingSeconds = 100
    )
  }
}
