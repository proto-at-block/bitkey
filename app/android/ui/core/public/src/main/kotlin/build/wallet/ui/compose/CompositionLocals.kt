package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Same [CompositionLocal.current] but operates on nullable composition locals and throws if
 * the value is null.
 */
@ReadOnlyComposable
@Composable
fun <T> CompositionLocal<T?>.requireCurrent(): T =
  requireNotNull(current) {
    "CompositionLocal $this is not provided in the composition."
  }
