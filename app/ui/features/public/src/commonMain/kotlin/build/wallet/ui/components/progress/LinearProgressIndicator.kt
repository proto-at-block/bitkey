package build.wallet.ui.components.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

@Composable
fun LinearProgressIndicator(
  modifier: Modifier = Modifier,
  // TODO(W-8034): use Progress type.
  progress: Float,
  indicatorColor: Color = WalletTheme.colors.bitkeyPrimary,
  backgroundColor: Color = WalletTheme.colors.foreground10,
  height: Dp = 8.dp,
) {
  Canvas(
    modifier = modifier
      .progressSemantics(progress)
      .fillMaxWidth()
      .height(height)
  ) {
    val strokeWidth = size.height
    val halfStroke = strokeWidth / 2f

    // Draw background track with rounded corners
    drawRoundRect(
      color = backgroundColor,
      topLeft = Offset.Zero,
      size = size,
      cornerRadius = CornerRadius(halfStroke, halfStroke)
    )

    // Draw progress indicator with rounded cap
    if (progress > 0f) {
      val coercedProgress = progress.coerceIn(0f, 1f)
      // Account for rounded caps on both ends: available width is reduced by strokeWidth
      val availableWidth = size.width - strokeWidth
      val progressWidth = availableWidth * coercedProgress

      // Draw line with rounded cap
      // Start and end points are offset by halfStroke to keep caps within bounds
      drawLine(
        color = indicatorColor,
        start = Offset(halfStroke, halfStroke),
        end = Offset(halfStroke + progressWidth, halfStroke),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
      )
    }
  }
}
