package build.wallet.ui.components.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.ui.tooling.PreviewWalletTheme
import com.github.michaelbull.result.getOrThrow

@Preview
@Composable
fun TimerZeroProgressPreview() {
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
