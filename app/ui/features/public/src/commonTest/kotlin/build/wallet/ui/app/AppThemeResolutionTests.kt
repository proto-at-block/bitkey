package build.wallet.ui.app

import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppThemeResolutionTests : FunSpec({
  test("manual screen theme overrides app theme") {
    effectiveTheme(
      appTheme = Theme.LIGHT,
      screenThemePreference = ThemePreference.Manual(Theme.DARK)
    ).shouldBe(Theme.DARK)

    effectiveTheme(
      appTheme = Theme.DARK,
      screenThemePreference = ThemePreference.Manual(Theme.LIGHT)
    ).shouldBe(Theme.LIGHT)
  }

  test("missing screen theme preference uses app theme") {
    effectiveTheme(
      appTheme = Theme.DARK,
      screenThemePreference = null
    ).shouldBe(Theme.DARK)
  }

  test("system screen theme preference uses app theme") {
    effectiveTheme(
      appTheme = Theme.LIGHT,
      screenThemePreference = ThemePreference.System
    ).shouldBe(Theme.LIGHT)
  }
})
