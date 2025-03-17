package build.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Represents app's theme mode.
 */
enum class Theme {
  LIGHT,
  DARK,
}

sealed interface ThemePreference {
  data object System : ThemePreference

  data class Manual(val value: Theme) : ThemePreference
}

/**
 * Returns [Theme] preferred by the OS. However, this does not define
 * the theme that the app will use. Instead, the app relies on [ThemePreference].
 */
@Composable
@ReadOnlyComposable
fun systemTheme(): Theme =
  when {
    isSystemInDarkTheme() -> Theme.DARK
    else -> Theme.LIGHT
  }
