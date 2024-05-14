package build.wallet.ui.components.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.core.form.FormMainContentModel.Timer
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import com.github.michaelbull.result.getOrThrow

@Composable
fun Timer(
  modifier: Modifier = Modifier,
  model: Timer,
) {
  Timer(
    modifier = modifier,
    title = model.title,
    subtitle = model.subtitle,
    progress = model.timerProgress,
    direction = model.direction,
    remainingSeconds = model.timerRemainingSeconds,
    size = 268.dp
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
  size: Dp,
) {
  Timer(
    modifier = modifier,
    progress = progress,
    direction = direction,
    remainingSeconds = remainingSeconds,
    size = size
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Label(
        text = title,
        type = LabelType.Title1
      )
      Spacer(Modifier.height(4.dp))
      Label(
        text = subtitle,
        type = LabelType.Body3Regular,
        treatment = LabelTreatment.Secondary
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
  size: Dp,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    // Full background circle.
    CircularProgressIndicator(
      size = size,
      progress = progress.value,
      direction = direction,
      remainingSeconds = remainingSeconds
    )

    content()
  }
}

@Preview
@Composable
internal fun TimerZeroProgressPreview() {
  PreviewWalletTheme {
    Timer(
      title = "14 days",
      subtitle = "Remaining",
      progress = Progress.Zero,
      direction = CounterClockwise,
      remainingSeconds = 10,
      size = 200.dp
    )
  }
}

@Preview
@Composable
internal fun TimerSomeProgressAnimatedPreview() {
  // Fake animated state to enable animation preview in IDE.
  animateFloatAsState(targetValue = 0f)
  PreviewWalletTheme {
    Timer(
      title = "8 days, 17 hours",
      subtitle = "Remaining",
      progress = 0.78F.asProgress().getOrThrow(),
      direction = CounterClockwise,
      remainingSeconds = 10,
      size = 200.dp
    )
  }
}
