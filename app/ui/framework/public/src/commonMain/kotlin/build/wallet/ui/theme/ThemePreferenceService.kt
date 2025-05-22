package build.wallet.ui.theme

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 *  A service for managing the current theme preference.
 */
interface ThemePreferenceService {
  /**
   * Returns a flow of [ThemePreference] representing the currently selected theme.
   * The preference can be either `System`, `Manual(Theme.LIGHT)`, or `Manual(Theme.DARK)`.
   */
  fun themePreference(): Flow<ThemePreference>

  /**
   * Returns a flow of [Theme] representing the currently selected theme.
   */
  fun theme(): Flow<Theme>

  /**
   * Sets the user's theme preference.
   */
  suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error>

  /**
   * Clears the user's theme preference, reverting to the default (System).
   */
  suspend fun clearThemePreference(): Result<Unit, Error>

  /**
   * Sets the current theme provided by the platform - this value is internally stored in memory.
   */
  fun setSystemTheme(systemTheme: Theme)
}
