package build.wallet.ui

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class ThemePreferenceDaoImplTests : FunSpec({

  fun TestScope.dao(): ThemePreferenceDaoImpl {
    return ThemePreferenceDaoImpl(KeyValueStoreFactoryFake(), createBackgroundScope())
  }

  test("get returns System theme when no entry exists") {
    dao().getThemePreference() shouldBe Ok(ThemePreference.System)
  }

  test("set and get System theme preference") {
    val dao = dao()
    dao.setThemePreference(ThemePreference.System) shouldBe Ok(Unit)
    dao.getThemePreference() shouldBe Ok(ThemePreference.System)
  }

  test("set and get Manual theme preference") {
    val dao = dao()
    dao.setThemePreference(ThemePreference.Manual(Theme.LIGHT)) shouldBe Ok(Unit)
    dao.getThemePreference() shouldBe Ok(ThemePreference.Manual(Theme.LIGHT))
  }

  test("clear theme preference resets to System") {
    val dao = dao()
    dao.setThemePreference(ThemePreference.Manual(Theme.DARK)) shouldBe Ok(Unit)
    dao.clearThemePreference() shouldBe Ok(Unit)
    dao.getThemePreference() shouldBe Ok(ThemePreference.System)
  }

  test("themePreference flow emits updates") {
    val dao = dao()
    dao.themePreference().test {
      awaitUntil(ThemePreference.System)

      dao.setThemePreference(ThemePreference.Manual(Theme.DARK)) shouldBe Ok(Unit)
      awaitUntil(ThemePreference.Manual(Theme.DARK))
    }
  }
})
