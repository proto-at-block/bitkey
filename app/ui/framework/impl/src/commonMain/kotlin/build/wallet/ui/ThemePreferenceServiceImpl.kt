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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class ThemePreferenceServiceImpl(
  private val themePreferenceDao: ThemePreferenceDao,
  private val darkModeFeatureFlag: DarkModeFeatureFlag,
) : ThemePreferenceService {
  override val isThemePreferenceEnabled: Boolean
    get() = darkModeFeatureFlag.isEnabled()

  override fun theme(): Flow<ThemePreference> =
    darkModeFeatureFlag.flagValue().flatMapLatest { flag ->
      if (flag.value) {
        themePreferenceDao.themePreference().map { it ?: ThemePreference.System }
      } else {
        flowOf(ThemePreference.Manual(Theme.LIGHT))
      }
    }

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    return themePreferenceDao.setThemePreference(themePreference)
  }

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    return themePreferenceDao.clearThemePreference()
  }
}
