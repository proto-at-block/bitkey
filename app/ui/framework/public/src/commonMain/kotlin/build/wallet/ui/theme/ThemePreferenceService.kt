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
  fun theme(): Flow<ThemePreference>

  /**
   * Returns whether the theme preference feature is enabled.
   */
  val isThemePreferenceEnabled: Boolean

  /**
   * Sets the user's theme preference.
   */
  suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error>

  /**
   * Clears the user's theme preference, reverting to the default (System).
   */
  suspend fun clearThemePreference(): Result<Unit, Error>
}
