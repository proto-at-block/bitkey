package build.wallet.ui.theme

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ThemePreferenceServiceFake(
  initialThemePreference: ThemePreference = ThemePreference.System,
) : ThemePreferenceService {
  private val _themePreference = MutableStateFlow(initialThemePreference)

  private var systemTheme = Theme.LIGHT

  override fun themePreference(): Flow<ThemePreference> =
    MutableStateFlow(ThemePreference.Manual(Theme.LIGHT))

  override fun theme(): Flow<Theme> {
    return when (_themePreference.value) {
      is ThemePreference.System -> MutableStateFlow(systemTheme)
      is ThemePreference.Manual -> MutableStateFlow((_themePreference.value as ThemePreference.Manual).value)
    }
  }

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    _themePreference.value = themePreference
    return Ok(Unit)
  }

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    _themePreference.value = ThemePreference.System
    return Ok(Unit)
  }

  override fun setSystemTheme(systemTheme: Theme) {
    this.systemTheme = systemTheme
  }
}
