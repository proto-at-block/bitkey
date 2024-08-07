package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import build.wallet.ui.theme.SystemColorMode
import build.wallet.ui.theme.SystemColorMode.DARK
import build.wallet.ui.theme.SystemColorMode.LIGHT

internal val LocalColors = staticCompositionLocalOf { lightStyleDictionaryColors }

/**
 * Maps [SystemColorMode] to corresponding set of colors.
 */
@Composable
@ReadOnlyComposable
internal fun SystemColorMode.colors(): StyleDictionaryColors =
  when (this) {
    LIGHT -> lightStyleDictionaryColors
    DARK -> darkStyleDictionaryColors
  }
