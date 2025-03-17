package build.wallet.compose.coroutines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope

/**
 * Provides stable [CoroutineScope] scope instance that survives recompositions.
 *
 * Regular [rememberCoroutineScope] creates a [CoroutineScope] that does not survive
 * recompositions, which can cause unexpected behavior for coroutines that need to
 * run across recompositions.
 */
@Composable
fun rememberStableCoroutineScope(): CoroutineScope {
  val scope = rememberCoroutineScope()
  return remember { StableCoroutineScope(scope) }
}

@Stable
private class StableCoroutineScope(scope: CoroutineScope) : CoroutineScope by scope
