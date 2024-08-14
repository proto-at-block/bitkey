package build.wallet.ui.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/**
 * Wrap the [content] Composable size without placing it on screen.
 */
@Composable
fun MeasureWithoutPlacement(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Layout(
    content = { Box(modifier = modifier) { content() } }
  ) { measurables, constraints ->
    val box = measurables.map { it.measure(constraints) }.single()
    layout(box.measuredWidth, box.measuredHeight) {
    }
  }
}
