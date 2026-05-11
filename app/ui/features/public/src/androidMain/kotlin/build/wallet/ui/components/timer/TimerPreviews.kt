package build.wallet.ui.components.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.core.form.FormMainContentModel.Timer.Display.RemainingDuration
import build.wallet.ui.tooling.PreviewWalletTheme
import com.github.michaelbull.result.getOrThrow
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Preview
@Composable
fun TimerZeroProgressPreview() {
  PreviewWalletTheme {
    val remainingDuration = 14.days
    Timer(
      title = "",
      subtitle = "",
      progress = Progress.Zero,
      direction = CounterClockwise,
      remainingSeconds = remainingDuration.inWholeSeconds,
      display = RemainingDuration(
        duration = remainingDuration,
        enableLocalSecondsTick = false
      )
    )
  }
}

@Preview
@Composable
fun TimerFinalMinutePreview() {
  PreviewWalletTheme {
    val remainingDuration = 42.seconds
    Timer(
      title = "",
      subtitle = "",
      progress = 0.9F.asProgress().getOrThrow(),
      direction = CounterClockwise,
      remainingSeconds = remainingDuration.inWholeSeconds,
      display = RemainingDuration(
        duration = remainingDuration,
        enableLocalSecondsTick = true
      )
    )
  }
}

@Preview
@Composable
fun TimerAlmostFinalMinutePreview() {
  PreviewWalletTheme {
    val remainingDuration = 119.seconds
    Timer(
      title = "",
      subtitle = "",
      progress = 0.85F.asProgress().getOrThrow(),
      direction = CounterClockwise,
      remainingSeconds = remainingDuration.inWholeSeconds,
      display = RemainingDuration(
        duration = remainingDuration,
        enableLocalSecondsTick = true
      )
    )
  }
}

@Preview
@Composable
fun TimerMinutesPreview() {
  PreviewWalletTheme {
    val remainingDuration = 3.minutes
    Timer(
      title = "",
      subtitle = "",
      progress = 0.7F.asProgress().getOrThrow(),
      direction = CounterClockwise,
      remainingSeconds = remainingDuration.inWholeSeconds,
      display = RemainingDuration(
        duration = remainingDuration,
        enableLocalSecondsTick = false
      )
    )
  }
}

@Preview
@Composable
internal fun TimerSomeProgressAnimatedPreview() {
  // Fake animated state to enable animation preview in IDE.
  animateFloatAsState(targetValue = 0f)
  PreviewWalletTheme {
    val remainingDuration = 8.days + 17.hours
    Timer(
      title = "",
      subtitle = "",
      progress = 0.78F.asProgress().getOrThrow(),
      direction = CounterClockwise,
      remainingSeconds = remainingDuration.inWholeSeconds,
      display = RemainingDuration(
        duration = remainingDuration,
        enableLocalSecondsTick = false
      )
    )
  }
}
