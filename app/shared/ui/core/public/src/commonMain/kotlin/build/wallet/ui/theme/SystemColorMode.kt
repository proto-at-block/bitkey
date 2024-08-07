package build.wallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import build.wallet.ui.theme.SystemColorMode.LIGHT

/**
 * A system-wide appearance modes.
 */
enum class SystemColorMode {
  LIGHT,
  DARK,
}

/**
 * Returns current [SystemColorMode] defined by the system.
 * TODO Update once we have full dark mode support
 */
// We only support Light theme for now so override phone's preference
@Composable
@ReadOnlyComposable
fun systemColorMode(): SystemColorMode = LIGHT
