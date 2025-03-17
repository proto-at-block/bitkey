package build.wallet.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.Theme.DARK
import build.wallet.ui.theme.Theme.LIGHT

internal val LocalColors = staticCompositionLocalOf { lightStyleDictionaryColors }

/**
 * Maps [Theme] to corresponding set of colors.
 */
@Composable
@ReadOnlyComposable
internal fun Theme.colors(): StyleDictionaryColors =
  when (this) {
    LIGHT -> lightStyleDictionaryColors
    DARK -> darkStyleDictionaryColors
  }
