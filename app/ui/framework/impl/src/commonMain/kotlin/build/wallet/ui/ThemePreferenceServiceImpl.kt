package build.wallet.ui

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceDao
import build.wallet.ui.theme.ThemePreferenceService
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class ThemePreferenceServiceImpl(
  private val themePreferenceDao: ThemePreferenceDao,
) : ThemePreferenceService {
  private val systemTheme = MutableStateFlow(Theme.LIGHT)

  override fun themePreference(): Flow<ThemePreference> =
    themePreferenceDao.themePreference()
      .map { it ?: ThemePreference.System }

  override fun theme(): Flow<Theme> {
    return combine(themePreference(), systemTheme) { themePreference, systemTheme ->
      when (themePreference) {
        is ThemePreference.System -> systemTheme
        is ThemePreference.Manual -> themePreference.value
      }
    }
  }

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    return themePreferenceDao.setThemePreference(themePreference)
  }

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    return themePreferenceDao.clearThemePreference()
  }

  override fun setSystemTheme(systemTheme: Theme) {
    this.systemTheme.value = systemTheme
  }
}
