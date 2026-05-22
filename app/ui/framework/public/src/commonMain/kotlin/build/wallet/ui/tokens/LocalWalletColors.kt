package build.wallet.ui.tokens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.Theme.DARK
import build.wallet.ui.theme.Theme.LIGHT

internal val LocalColors = staticCompositionLocalOf { lightStyleDictionaryColors }

/**
 * Returns the default background color for [Theme].
 */
fun Theme.backgroundColor(): Color =
  colors().background

/**
 * Maps [Theme] to corresponding set of colors.
 */
internal fun Theme.colors(): StyleDictionaryColors =
  when (this) {
    LIGHT -> lightStyleDictionaryColorsDesignSystemUpdates
    DARK -> darkStyleDictionaryColors
  }
