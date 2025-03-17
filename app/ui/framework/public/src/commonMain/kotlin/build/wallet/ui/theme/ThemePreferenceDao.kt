package build.wallet.ui.theme

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceDao {
  /**
   * Sets the user's theme preference.
   */
  suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error>

  /**
   * Gets the user's current theme preference.
   * If not explicitly set, returns null.
   */
  suspend fun getThemePreference(): Result<ThemePreference?, Error>

  /**
   * Returns a flow of the user's current theme preference.
   */
  fun themePreference(): StateFlow<ThemePreference?>

  /**
   * Clears the user's theme preference.
   */
  suspend fun clearThemePreference(): Result<Unit, Error>
}
