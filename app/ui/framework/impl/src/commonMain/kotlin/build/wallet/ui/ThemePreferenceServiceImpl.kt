package build.wallet.ui

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.DarkModeFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceDao
import build.wallet.ui.theme.ThemePreferenceService
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.*

@BitkeyInject(ActivityScope::class)
class ThemePreferenceServiceImpl(
  private val themePreferenceDao: ThemePreferenceDao,
  private val darkModeFeatureFlag: DarkModeFeatureFlag,
) : ThemePreferenceService {
  override val isThemePreferenceEnabled: Boolean
    get() = darkModeFeatureFlag.isEnabled()

  private val systemTheme = MutableStateFlow(Theme.LIGHT)

  override fun themePreference(): Flow<ThemePreference> =
    darkModeFeatureFlag.flagValue().flatMapLatest { flag ->
      if (flag.value) {
        themePreferenceDao.themePreference().map { it ?: ThemePreference.System }
      } else {
        flowOf(ThemePreference.Manual(Theme.LIGHT))
      }
    }

  override fun theme(): Flow<Theme> {
    return darkModeFeatureFlag.flagValue().flatMapLatest { flag ->
      if (!flag.value) {
        // If dark mode feature flag is disabled, always use light theme
        // regardless of system theme or user preference
        flowOf(Theme.LIGHT)
      } else {
        combine(themePreference(), systemTheme) { themePreference, systemTheme ->
          when (themePreference) {
            is ThemePreference.System -> systemTheme
            is ThemePreference.Manual -> themePreference.value
          }
        }
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
