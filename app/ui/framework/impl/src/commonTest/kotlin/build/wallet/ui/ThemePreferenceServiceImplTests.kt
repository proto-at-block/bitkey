package build.wallet.ui

import app.cash.turbine.test
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.DarkModeFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceDaoFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ThemePreferenceServiceImplTests : FunSpec({
  lateinit var themePreferenceDao: ThemePreferenceDaoFake
  lateinit var darkModeFeatureFlag: DarkModeFeatureFlag
  lateinit var service: ThemePreferenceServiceImpl

  beforeTest {
    darkModeFeatureFlag = DarkModeFeatureFlag(
      featureFlagDao = FeatureFlagDaoMock()
    )
    themePreferenceDao = ThemePreferenceDaoFake()
    service = ThemePreferenceServiceImpl(themePreferenceDao, darkModeFeatureFlag)
  }

  test("isThemePreferenceEnabled reflects the dark mode flag") {
    darkModeFeatureFlag.setFlagValue(true)
    service.isThemePreferenceEnabled shouldBe true

    darkModeFeatureFlag.setFlagValue(false)
    service.isThemePreferenceEnabled shouldBe false
  }

  test("theme() returns System when dark mode flag is enabled and DAO returns null") {
    darkModeFeatureFlag.setFlagValue(true)
    service.theme().test {
      awaitItem() shouldBe ThemePreference.System
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("theme() returns the DAO value when dark mode flag is enabled") {
    darkModeFeatureFlag.setFlagValue(true)
    themePreferenceDao.setThemePreference(ThemePreference.Manual(Theme.DARK))

    service.theme().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("theme() returns Manual(LIGHT) when dark mode flag is disabled regardless of DAO value") {
    darkModeFeatureFlag.setFlagValue(false)
    // Even if the DAO has a dark theme, the service should return a light theme.
    themePreferenceDao.setThemePreference(ThemePreference.Manual(Theme.DARK))

    service.theme().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.LIGHT)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("setThemePreference calls DAO.setThemePreference for Manual themes") {
    val result = service.setThemePreference(ThemePreference.Manual(Theme.DARK))
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("setThemePreference calls DAO.setThemePreference for System themes") {
    val result = service.setThemePreference(ThemePreference.System)
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem() shouldBe ThemePreference.System
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("clearThemePreference calls DAO.clearThemePreference") {
    themePreferenceDao.setThemePreference(ThemePreference.Manual(Theme.DARK))
    val result = service.clearThemePreference()
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem().shouldBeNull()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
