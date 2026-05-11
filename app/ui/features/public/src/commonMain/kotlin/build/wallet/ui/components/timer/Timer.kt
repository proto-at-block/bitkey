package build.wallet.ui.components.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.Progress
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.core.form.FormMainContentModel.Timer
import build.wallet.statemachine.core.form.FormMainContentModel.Timer.Display
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun Timer(
  modifier: Modifier = Modifier,
  model: Timer,
) {
  Timer(
    modifier = modifier,
    title = model.title,
    subtitle = model.subtitle,
    display = model.display,
    progress = model.timerProgress,
    direction = model.direction,
    remainingSeconds = model.timerRemainingSeconds,
    indicatorColor = when (model.style) {
      Timer.Style.PRIMARY -> WalletTheme.colors.bitkeyPrimary
      Timer.Style.FOREGROUND -> WalletTheme.colors.foreground
    },
    size = TimerSize
  )
}

@Composable
fun Timer(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  progress: Progress,
  direction: TimerDirection,
  remainingSeconds: Long,
  indicatorColor: Color = WalletTheme.colors.bitkeyPrimary,
  size: Dp = TimerSize,
  display: Display = Display.Text(title = title, subtitle = subtitle),
) {
  Timer(
    modifier = modifier,
    progress = progress,
    direction = direction,
    remainingSeconds = remainingSeconds,
    indicatorColor = indicatorColor,
    size = size
  ) {
    val timerText = rememberTimerText(display)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Label(
        text = timerText.title,
        style = WalletTheme.labelStyle(
          type = LabelType.Display2,
          alignment = TextAlign.Center
        ).trimLineHeight()
      )
      Spacer(Modifier.height(4.dp))
      Label(
        text = timerText.subtitle,
        style = WalletTheme.labelStyle(
          type = LabelType.Body2Regular,
          treatment = LabelTreatment.Secondary,
          alignment = TextAlign.Center
        ).trimLineHeight()
      )
    }
  }
}

@Composable
internal fun Timer(
  modifier: Modifier = Modifier,
  progress: Progress,
  direction: TimerDirection,
  remainingSeconds: Long,
  indicatorColor: Color = WalletTheme.colors.bitkeyPrimary,
  size: Dp,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    TimerBackgroundTrack(size = size)

    CircularProgressIndicator(
      size = size,
      progress = progress.value,
      direction = direction,
      remainingSeconds = remainingSeconds,
      indicatorColor = indicatorColor,
      backgroundColor = Color.Transparent
    )

    Box(
      modifier = Modifier.offset(y = TimerContentOpticalOffset),
      contentAlignment = Alignment.Center
    ) {
      content()
    }
  }
}

@Composable
private fun TimerBackgroundTrack(size: Dp) {
  val color = WalletTheme.colors.foreground10
  Canvas(modifier = Modifier.size(size)) {
    val activeStrokeWidth = (size / 16.6f).toPx()
    val radius = (min(this.size.width, this.size.height) - activeStrokeWidth) / 2
    drawCircle(
      color = color,
      radius = radius,
      style = Stroke(
        width = TimerBackgroundStrokeWidth.toPx(),
        cap = StrokeCap.Round
      )
    )
  }
}

private val TimerSize = 324.dp
private val TimerBackgroundStrokeWidth = 3.dp
private val TimerContentOpticalOffset = (-12).dp

private data class TimerText(
  val title: String,
  val subtitle: String,
)

@Composable
private fun rememberTimerText(
  display: Display,
): TimerText {
  return when (display) {
    is Display.Text -> TimerText(
      title = display.title,
      subtitle = display.subtitle
    )
    is Display.RemainingDuration -> rememberTimerText(display)
  }
}

@Composable
private fun rememberTimerText(
  display: Display.RemainingDuration,
): TimerText {
  val initialSeconds = display.duration.inWholeSeconds.coerceAtLeast(0)
  var secondsRemaining by remember(display.enableLocalSecondsTick, initialSeconds) {
    mutableLongStateOf(initialSeconds)
  }

  LaunchedEffect(display.enableLocalSecondsTick, display.duration) {
    if (!display.enableLocalSecondsTick) return@LaunchedEffect

    secondsRemaining = initialSeconds
    while (secondsRemaining > 0) {
      delay(1.seconds)
      secondsRemaining -= 1
    }
  }

  val localDurationRemaining = secondsRemaining.seconds
  return if (
    display.enableLocalSecondsTick &&
    localDurationRemaining <= display.showSecondsBelow
  ) {
    secondsRemaining.toSecondsTimerText(display.subtitle)
  } else {
    localDurationRemaining.toTimerText(display.subtitle)
  }
}

private fun androidx.compose.ui.text.TextStyle.trimLineHeight() =
  copy(
    lineHeightStyle = LineHeightStyle(
      alignment = LineHeightStyle.Alignment.Center,
      trim = LineHeightStyle.Trim.Both
    )
  )

private fun Duration.toTimerText(subtitle: String): TimerText {
  val nonNegativeDuration = inWholeSeconds.coerceAtLeast(0).seconds
  return nonNegativeDuration.toComponents { days, hours, minutes, _, _ ->
    when {
      days > 0 ->
        TimerText(
          title = days.toString(),
          subtitle = durationUnitLabel(
            primaryUnit = days.unitLabel("day", "days"),
            remainder = hours.remainderLabel("hour", "hours"),
            subtitle = subtitle
          )
        )
      hours > 0 ->
        TimerText(
          title = hours.toString(),
          subtitle = durationUnitLabel(
            primaryUnit = hours.unitLabel("hour", "hours"),
            remainder = minutes.remainderLabel("minute", "minutes"),
            subtitle = subtitle
          )
        )
      minutes > 0 ->
        TimerText(
          title = minutes.toString(),
          subtitle = minutes.unitLabel("minute", "minutes").withTimerSubtitle(subtitle)
        )
      else ->
        TimerText(
          title = "<1",
          subtitle = "minute".withTimerSubtitle(subtitle)
        )
    }
  }
}

private fun Long.toSecondsTimerText(subtitle: String): TimerText =
  TimerText(
    title = toString(),
    subtitle = unitLabel("second", "seconds").withTimerSubtitle(subtitle)
  )

private fun durationUnitLabel(
  primaryUnit: String,
  remainder: String?,
  subtitle: String,
): String {
  val unit = listOfNotNull(primaryUnit, remainder)
    .joinToString(separator = ", ")
  return unit.withTimerSubtitle(subtitle)
}

private fun Int.remainderLabel(
  singular: String,
  plural: String,
): String? =
  takeIf { it > 0 }?.let { "${it} ${it.unitLabel(singular, plural)}" }

private fun Long.unitLabel(
  singular: String,
  plural: String,
): String =
  if (this == 1L) singular else plural

private fun Int.unitLabel(
  singular: String,
  plural: String,
): String =
  toLong().unitLabel(singular, plural)

private fun String.withTimerSubtitle(subtitle: String): String =
  listOf(
    this,
    subtitle.replaceFirstChar { it.lowercase() }
  ).filter { it.isNotBlank() }
    .joinToString(separator = " ")
