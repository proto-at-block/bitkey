package build.wallet.ui.components.progress

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.material.LinearProgressIndicator as MaterialLinearProgressIndicator

@Composable
fun LinearProgressIndicator(
  modifier: Modifier = Modifier,
  // TODO(W-8034): use Progress type.
  progress: Float,
  indicatorColor: Color = WalletTheme.colors.bitkeyPrimary,
  backgroundColor: Color = WalletTheme.colors.foreground10,
  height: Dp = 8.dp,
) {
  MaterialLinearProgressIndicator(
    modifier = modifier.height(height),
    progress = progress,
    color = indicatorColor,
    backgroundColor = backgroundColor,
    strokeCap = StrokeCap.Round
  )
}

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
