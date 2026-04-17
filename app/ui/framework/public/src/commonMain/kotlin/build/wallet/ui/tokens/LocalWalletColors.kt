package build.wallet.ui.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.Theme.DARK
import build.wallet.ui.theme.Theme.LIGHT

internal val LocalColors = staticCompositionLocalOf { lightStyleDictionaryColors }

/**
 * Returns the default background color for [Theme] and design system update state.
 */
fun Theme.backgroundColor(
  designSystemUpdatesEnabled: Boolean,
): Color =
  colors(designSystemUpdatesEnabled).background

/**
 * Maps [Theme] to corresponding set of colors.
 */
internal fun Theme.colors(
  designSystemUpdatesEnabled: Boolean,
): StyleDictionaryColors =
  when (this) {
    LIGHT ->
      if (designSystemUpdatesEnabled) {
        lightStyleDictionaryColorsDesignSystemUpdates
      } else {
        lightStyleDictionaryColors
      }
    DARK -> darkStyleDictionaryColors
  }

/**
 * Maps [Theme] to corresponding set of colors using the current design system update flag.
 */
@Composable
@ReadOnlyComposable
internal fun Theme.colors(): StyleDictionaryColors =
  colors(designSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current)
