package build.wallet.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal used to pass the current [Theme] down the tree.
 *
 * Can be either [Theme.LIGHT] or [Theme.DARK].
 */
@Suppress("CompositionLocalAllowlist")
val LocalTheme = staticCompositionLocalOf<Theme> { error("Theme was not provided.") }
