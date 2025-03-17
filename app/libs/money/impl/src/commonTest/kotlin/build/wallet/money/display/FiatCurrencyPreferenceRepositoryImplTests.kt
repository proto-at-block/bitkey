@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.money.display

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.currency.*
import build.wallet.platform.settings.LocaleCurrencyCodeProviderFake
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi

class FiatCurrencyPreferenceRepositoryImplTests : FunSpec({
  val localeCurrencyCodeProvider = LocaleCurrencyCodeProviderFake()

  lateinit var fiatCurrencyDao: FiatCurrencyDao
  lateinit var fiatCurrencyPreferenceDao: FiatCurrencyPreferenceDao

  fun TestScope.repository(): FiatCurrencyPreferenceRepository {
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

    fiatCurrencyDao = FiatCurrencyDaoImpl(databaseProvider)
    fiatCurrencyPreferenceDao = FiatCurrencyPreferenceDaoImpl(databaseProvider)

    return FiatCurrencyPreferenceRepositoryImpl(
      appScope = createBackgroundScope(),
      fiatCurrencyPreferenceDao = fiatCurrencyPreferenceDao,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider,
      fiatCurrencyDao = fiatCurrencyDao
    )
  }

  beforeTest {
    localeCurrencyCodeProvider.reset()
  }

  context("device locale is not available") {
    test("fiatCurrencyPreference defaults to USD") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = null
      val repository = repository()

      repository.fiatCurrencyPreference.test {
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }
    }
  }

  context("device locale is available") {
    test("fiatCurrencyPreference defaults to currency based on the device locale") {
      val repository = repository()

      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))

      repository.fiatCurrencyPreference.test {
        // Default USD is not guaranteed due to concurrency, so we are just waiting for GBP.
        awaitUntil(GBP)
      }
    }

    test("fiatCurrencyPreference defaults to USD when a currency for device locale is not available") {
      val repository = repository()

      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      fiatCurrencyDao.storeFiatCurrencies(listOf(EUR))

      repository.fiatCurrencyPreference.test {
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }
    }
  }

  test("new preference is emitted when dao values is changed") {
    val repository = repository()

    repository.fiatCurrencyPreference.test {
      awaitItem().shouldBe(USD) // initial default value.

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

      awaitItem().shouldBe(GBP)

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP, EUR))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(EUR)

      awaitItem().shouldBe(EUR)
    }
  }

  test("preference is reset to USD when dao value is cleared") {
    val repository = repository()

    repository.fiatCurrencyPreference.test {
      awaitItem().shouldBe(USD) // initial default value.

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

      awaitItem().shouldBe(GBP)

      fiatCurrencyPreferenceDao.clear()

      awaitItem().shouldBe(USD)
    }
  }

  test("clear removes preference from DAO") {
    val repository = repository()

    fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
    fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

    fiatCurrencyPreferenceDao.fiatCurrencyPreference().test {
      awaitItem().shouldBe(GBP) // initial default value.

      repository.clear()

      awaitItem().shouldBeNull()
    }
  }
})
