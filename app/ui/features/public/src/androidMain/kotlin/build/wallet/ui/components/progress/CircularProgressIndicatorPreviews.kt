package build.wallet.ui.components.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.TimerDirection.Clockwise
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.ui.tooling.PreviewWalletTheme

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
