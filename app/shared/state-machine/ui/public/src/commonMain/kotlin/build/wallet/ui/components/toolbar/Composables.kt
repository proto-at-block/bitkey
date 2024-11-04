package build.wallet.ui.components.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * https://stackoverflow.com/a/73282996/16459196
 */
@Composable
fun <T> rememberConditionally(
  condition: Boolean,
  block: @DisallowComposableCalls () -> T,
): T? {
  var content by remember { mutableStateOf<T?>(null) }

  if (condition) {
    content = block()
  }

  return content
}
