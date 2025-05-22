package build.wallet.ui

import app.cash.turbine.test
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceDaoFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ThemePreferenceServiceImplTests : FunSpec({
  lateinit var themePreferenceDao: ThemePreferenceDaoFake

  fun service() =
    ThemePreferenceServiceImpl(
      themePreferenceDao = themePreferenceDao
    )

  beforeTest {
    themePreferenceDao = ThemePreferenceDaoFake()
  }

  test("theme() returns System when and DAO returns null") {
    service().themePreference().test {
      awaitItem() shouldBe ThemePreference.System
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("theme() returns the DAO value") {
    themePreferenceDao.setThemePreference(ThemePreference.Manual(Theme.DARK))

    service().themePreference().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("setThemePreference calls DAO.setThemePreference for Manual themes") {
    val result = service().setThemePreference(ThemePreference.Manual(Theme.DARK))
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("setThemePreference calls DAO.setThemePreference for System themes") {
    val result = service().setThemePreference(ThemePreference.System)
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem() shouldBe ThemePreference.System
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("clearThemePreference calls DAO.clearThemePreference") {
    themePreferenceDao.setThemePreference(ThemePreference.Manual(Theme.DARK))
    val result = service().clearThemePreference()
    result shouldBe Ok(Unit)

    themePreferenceDao.themePreference().test {
      awaitItem().shouldBeNull()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("theme is appropriately updated when system updates") {
    val service = service()

    service.theme().test {
      // defaults to light
      awaitItem() shouldBe Theme.LIGHT

      service.setSystemTheme(Theme.DARK)

      awaitItem() shouldBe Theme.DARK

      service.setSystemTheme(Theme.LIGHT)

      awaitItem() shouldBe Theme.LIGHT
    }
  }

  test("theme() respects system theme when preference is System") {
    themePreferenceDao.setThemePreference(ThemePreference.System)

    // Set system theme to dark
    val service = service()
    service.setSystemTheme(Theme.DARK)

    service.theme().test {
      awaitItem() shouldBe Theme.DARK

      // Change system theme to light
      service.setSystemTheme(Theme.LIGHT)

      // Theme should follow system change
      awaitItem() shouldBe Theme.LIGHT
    }
  }
})
