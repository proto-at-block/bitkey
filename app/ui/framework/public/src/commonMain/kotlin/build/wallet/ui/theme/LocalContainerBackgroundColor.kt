package build.wallet.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Optional surface color for components embedded within a container (for example [Card]).
 *
 * This allows nested components to match their interop background to the containing surface
 * instead of defaulting to the app-level background.
 */
@Suppress("CompositionLocalAllowlist")
val LocalContainerBackgroundColor = compositionLocalOf<Color?> { null }
