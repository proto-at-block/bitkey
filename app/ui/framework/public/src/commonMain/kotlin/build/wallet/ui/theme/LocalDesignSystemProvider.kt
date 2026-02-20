package build.wallet.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal to indicate whether the new design system (V2) is enabled.
 * When true, UI components should use the new Cash Sans fonts and updated style tokens.
 * Default is false to preserve existing behavior.
 */
@Suppress("CompositionLocalAllowlist")
val LocalDesignSystemUpdatesEnabled = compositionLocalOf { false }
