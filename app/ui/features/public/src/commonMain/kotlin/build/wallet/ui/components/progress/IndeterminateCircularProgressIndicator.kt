package build.wallet.ui.components.progress

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

@Composable
fun IndeterminateCircularProgressIndicator(
  modifier: Modifier = Modifier,
  indicatorColor: Color,
  trackColor: Color,
  strokeWidth: Dp,
  size: Dp,
  strokeCap: StrokeCap = StrokeCap.Round,
) {
  CircularProgressIndicator(
    modifier = modifier.then(Modifier.size(size)),
    color = indicatorColor,
    strokeWidth = strokeWidth,
    trackColor = trackColor,
    strokeCap = strokeCap
  )
}
