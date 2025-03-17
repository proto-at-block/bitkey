package build.wallet.ui.theme

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemePreferenceDaoFake : ThemePreferenceDao {
  private val _themePreference = MutableStateFlow<ThemePreference?>(null)

  override fun themePreference(): StateFlow<ThemePreference?> = _themePreference

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    _themePreference.value = themePreference
    return Ok(Unit)
  }

  override suspend fun getThemePreference(): Result<ThemePreference, Error> {
    // If no value is set, default to System.
    return Ok(_themePreference.value ?: ThemePreference.System)
  }

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    _themePreference.value = null
    return Ok(Unit)
  }
}
