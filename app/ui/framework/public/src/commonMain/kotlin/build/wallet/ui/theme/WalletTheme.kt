package build.wallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import build.wallet.ui.tokens.LocalColors
import build.wallet.ui.tokens.StyleDictionaryColors
import build.wallet.ui.tokens.colors

/**
 * A composable that provides [content] with our themed colors, and other UI primitives.
 *
 * Usage:
 *
 * ```kotlin
 * WalletTheme {
 *   Button(text = "Hello, wallet!", treatment = PRIMARY, onClick = { })
 * }
 * ```
 */
@Composable
fun WalletTheme(content: @Composable () -> Unit) {
  val colors = LocalTheme.current.colors()
  CompositionLocalProvider(
    LocalColors provides colors
  ) {
    content()
  }
}

/**
 * Contains convenience functions for accessing current theme values, such as colors.
 */
object WalletTheme {
  /**
   * Returns the current [StyleDictionaryColors].
   */
  val colors: StyleDictionaryColors
    @Composable
    @ReadOnlyComposable
    get() = LocalColors.current
}
