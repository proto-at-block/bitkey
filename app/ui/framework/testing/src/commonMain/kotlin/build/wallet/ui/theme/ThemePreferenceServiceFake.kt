package build.wallet.ui.theme

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ThemePreferenceServiceFake(
  private var darkModeEnabled: Boolean = true,
  initialThemePreference: ThemePreference = ThemePreference.System,
) : ThemePreferenceService {
  private val _themePreference = MutableStateFlow(initialThemePreference)

  override val isThemePreferenceEnabled: Boolean
    get() = darkModeEnabled

  override fun theme(): Flow<ThemePreference> =
    if (darkModeEnabled) {
      _themePreference
    } else {
      MutableStateFlow(ThemePreference.Manual(Theme.LIGHT))
    }

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    if (darkModeEnabled) {
      _themePreference.value = themePreference
    }
    return Ok(Unit)
  }

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    if (darkModeEnabled) {
      _themePreference.value = ThemePreference.System
    }
    return Ok(Unit)
  }
}
