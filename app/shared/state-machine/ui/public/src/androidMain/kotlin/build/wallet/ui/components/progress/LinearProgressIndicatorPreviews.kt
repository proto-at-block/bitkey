package build.wallet.ui.components.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun PreviewLinearProgressIndicatorEmpty() {
  LinearProgressIndicator(
    progress = 0f
  )
}

@Preview
@Composable
fun PreviewLinearProgressIndicatorHalf() {
  LinearProgressIndicator(
    progress = .50f
  )
}

@Preview
@Composable
fun PreviewLinearProgressIndicatorFull() {
  LinearProgressIndicator(
    progress = 1f
  )
}
