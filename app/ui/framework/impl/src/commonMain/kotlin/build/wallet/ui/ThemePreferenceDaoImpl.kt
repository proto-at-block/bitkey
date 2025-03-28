package build.wallet.ui

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.store.KeyValueStoreFactory
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceDao
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class ThemePreferenceDaoImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
  coroutineScope: CoroutineScope,
) : ThemePreferenceDao {
  private companion object {
    const val THEME_SYSTEM = "SYSTEM"
    const val THEME_MANUAL_PREFIX = "MANUAL_"
    const val THEME_PREFERENCE_KEY = "theme_preference"
    const val THEME_PREFERENCE_STORE_NAME = "THEME_PREFERENCE_STORE"
  }

  private suspend fun getStore(): SuspendSettings =
    keyValueStoreFactory.getOrCreate(THEME_PREFERENCE_STORE_NAME)

  private val themePreferenceFlow = MutableStateFlow<ThemePreference>(ThemePreference.System)

  init {
    coroutineScope.launch {
      val store = getStore()
      val storedTheme = store.getString(THEME_PREFERENCE_KEY, THEME_SYSTEM)
      val parsedTheme = parseThemePreference(storedTheme)
      themePreferenceFlow.update { parsedTheme }
    }
  }

  override suspend fun setThemePreference(themePreference: ThemePreference): Result<Unit, Error> {
    val themePreferenceString = when (themePreference) {
      ThemePreference.System -> THEME_SYSTEM
      is ThemePreference.Manual -> "$THEME_MANUAL_PREFIX${themePreference.value}"
    }
    val store = getStore()
    store.putString(THEME_PREFERENCE_KEY, themePreferenceString)
    val parsedTheme = parseThemePreference(themePreferenceString)
    themePreferenceFlow.update { parsedTheme }
    return Ok(Unit)
  }

  override suspend fun getThemePreference(): Result<ThemePreference, Error> {
    val store = getStore()
    val themeStr = store.getString(THEME_PREFERENCE_KEY, THEME_SYSTEM)
    return Ok(parseThemePreference(themeStr))
  }

  override fun themePreference(): StateFlow<ThemePreference?> = themePreferenceFlow

  override suspend fun clearThemePreference(): Result<Unit, Error> {
    val store = getStore()
    store.remove(THEME_PREFERENCE_KEY)
    themePreferenceFlow.update { ThemePreference.System }
    return Ok(Unit)
  }

  private fun parseThemePreference(themeStr: String?): ThemePreference =
    when {
      themeStr == null || themeStr == THEME_SYSTEM -> ThemePreference.System
      themeStr.startsWith(THEME_MANUAL_PREFIX) -> {
        val themeName = themeStr.removePrefix(THEME_MANUAL_PREFIX)
        try {
          ThemePreference.Manual(Theme.valueOf(themeName))
        } catch (error: IllegalArgumentException) {
          logError(throwable = error) { "Unknown theme name: $themeName" }
          ThemePreference.System
        }
      }
      else -> {
        logError { "Unknown theme preference: $themeStr" }
        ThemePreference.System
      }
    }
}
