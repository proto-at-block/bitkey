package build.wallet.money.display

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.FiatCurrencyDaoImpl
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.platform.settings.LocaleCurrencyCodeProviderFake
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class FiatCurrencyPreferenceRepositoryImplTests : FunSpec({

  coroutineTestScope = true

  val localeCurrencyCodeProvider = LocaleCurrencyCodeProviderFake()

  lateinit var fiatCurrencyDao: FiatCurrencyDao
  lateinit var fiatCurrencyPreferenceDao: FiatCurrencyPreferenceDao

  fun TestScope.repository(): FiatCurrencyPreferenceRepository {
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

    fiatCurrencyDao = FiatCurrencyDaoImpl(databaseProvider)
    fiatCurrencyPreferenceDao = FiatCurrencyPreferenceDaoImpl(databaseProvider)

    return FiatCurrencyPreferenceRepositoryImpl(
      appScope = backgroundScope,
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
        awaitItem().shouldBe(USD) // initial default value.
        expectNoEvents()
      }
    }
  }

  context("device locale is available") {
    test("fiatCurrencyPreference defaults to currency based on the device locale") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      val repository = repository()

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))

      repository.fiatCurrencyPreference.test {
        awaitItem().shouldBe(USD) // initial default value.
        awaitItem().shouldBe(GBP)
      }
    }

    test("fiatCurrencyPreference defaults to USD when a currency for device locale is not available") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      val repository = repository()

      fiatCurrencyDao.storeFiatCurrencies(listOf(EUR))

      repository.fiatCurrencyPreference.test {
        awaitItem().shouldBe(USD) // initial default value.
        expectNoEvents()
      }
    }
  }

  test("new preference is emitted when dao values is changed") {
    val repository = repository()

    fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
    fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

    repository.fiatCurrencyPreference.test {
      awaitItem().shouldBe(USD) // initial default value.
      awaitItem().shouldBe(GBP)

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP, EUR))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(EUR)

      awaitItem().shouldBe(EUR)
    }
  }

  test("preference is reset to USD when dao value is cleared") {
    val repository = repository()

    fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
    fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

    repository.fiatCurrencyPreference.test {
      awaitItem().shouldBe(USD) // initial default value.
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
