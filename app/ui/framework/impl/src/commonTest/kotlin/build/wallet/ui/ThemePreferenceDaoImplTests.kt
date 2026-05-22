package build.wallet.ui

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.Ok
import com.russhwolf.settings.coroutines.SuspendSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class ThemePreferenceDaoImplTests : FunSpec({

  fun TestScope.dao(
    keyValueStoreFactory: KeyValueStoreFactory = KeyValueStoreFactoryFake(),
  ): ThemePreferenceDaoImpl {
    return ThemePreferenceDaoImpl(keyValueStoreFactory, createBackgroundScope())
  }

  test("construction does not touch storage") {
    val keyValueStoreFactory = CountingKeyValueStoreFactory()

    dao(keyValueStoreFactory)

    keyValueStoreFactory.getOrCreateCalls shouldBe 0
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

  test("first themePreference collection emits stored preference") {
    val keyValueStoreFactory = KeyValueStoreFactoryFake()
    dao(keyValueStoreFactory)
      .setThemePreference(ThemePreference.Manual(Theme.DARK)) shouldBe Ok(Unit)

    dao(keyValueStoreFactory).themePreference().test {
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
    }
  }

  test("themePreference flow emits updates") {
    val dao = dao()
    dao.themePreference().test {
      awaitItem() shouldBe ThemePreference.System

      dao.setThemePreference(ThemePreference.Manual(Theme.DARK)) shouldBe Ok(Unit)
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)
    }
  }

  test("themePreference flow emits rapid writes without rolling back") {
    val dao = dao()

    dao.themePreference().test {
      awaitItem() shouldBe ThemePreference.System

      dao.setThemePreference(ThemePreference.Manual(Theme.DARK)) shouldBe Ok(Unit)
      awaitItem() shouldBe ThemePreference.Manual(Theme.DARK)

      dao.setThemePreference(ThemePreference.System) shouldBe Ok(Unit)
      awaitItem() shouldBe ThemePreference.System
    }
  }
})

private class CountingKeyValueStoreFactory : KeyValueStoreFactory {
  private val delegate = KeyValueStoreFactoryFake()
  var getOrCreateCalls = 0
    private set

  override suspend fun getOrCreate(storeName: String): SuspendSettings {
    getOrCreateCalls += 1
    return delegate.getOrCreate(storeName)
  }
}
