@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.money.display

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.currency.*
import build.wallet.platform.settings.LocaleCurrencyCodeProviderFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class FiatCurrencyPreferenceRepositoryImplTests : FunSpec({
  val localeCurrencyCodeProvider = LocaleCurrencyCodeProviderFake()
  val fiatCurrencyPreferenceDao = FiatCurrencyPreferenceDaoFake()
  val fiatCurrencyDao = FiatCurrencyDaoFake()
  lateinit var testScope: TestScope
  lateinit var repository: FiatCurrencyPreferenceRepositoryImpl

  fun resetRepository() {
    testScope = TestScope()
    repository = FiatCurrencyPreferenceRepositoryImpl(
      appScope = testScope,
      fiatCurrencyPreferenceDao = fiatCurrencyPreferenceDao,
      localeCurrencyCodeProvider = localeCurrencyCodeProvider,
      fiatCurrencyDao = fiatCurrencyDao
    )
  }

  beforeTest {
    fiatCurrencyDao.storeFiatCurrencies(emptyList())
    fiatCurrencyPreferenceDao.clear()
    localeCurrencyCodeProvider.reset()
    resetRepository()
  }

  afterTest {
    testScope.cancel()
  }

  context("device locale is not available") {
    test("fiatCurrencyPreference defaults to USD") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = null

      repository.fiatCurrencyPreference.test {
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }
    }

    test("default USD value is saved to DAO") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "EUR"
      fiatCurrencyDao.storeFiatCurrencies(listOf(USD, GBP))

      repository.fiatCurrencyPreference.test {
        testScope.runCurrent()
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }

      resetRepository()
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      fiatCurrencyDao.storeFiatCurrencies(listOf(USD, GBP))
      repository.fiatCurrencyPreference.test {
        testScope.runCurrent()
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }
    }
  }

  context("device locale is available") {
    test("fiatCurrencyPreference defaults to currency based on the device locale") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))

      repository.fiatCurrencyPreference.test {
        testScope.runCurrent()
        // Default USD is not guaranteed due to concurrency, so we are just waiting for GBP.
        awaitUntil(GBP)
      }
    }

    test("fiatCurrencyPreference defaults to USD when a currency for device locale is not available") {
      localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "GBP"
      fiatCurrencyDao.storeFiatCurrencies(listOf(EUR))

      repository.fiatCurrencyPreference.test {
        testScope.runCurrent()
        awaitItem().shouldBe(USD)
        awaitNoEvents()
      }
    }
  }

  test("new preference is emitted when dao values is changed") {
    repository.fiatCurrencyPreference.test {
      testScope.runCurrent()
      awaitItem().shouldBe(USD) // initial default value.

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)
      testScope.runCurrent()

      awaitItem().shouldBe(GBP)

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP, EUR))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(EUR)
      testScope.runCurrent()

      awaitItem().shouldBe(EUR)
    }
  }

  test("preference is reset to USD when dao value is cleared") {
    repository.fiatCurrencyPreference.test {
      testScope.runCurrent()
      awaitItem().shouldBe(USD) // initial default value.

      fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
      fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)
      testScope.runCurrent()

      awaitItem().shouldBe(GBP)

      fiatCurrencyPreferenceDao.clear()
      testScope.runCurrent()

      awaitItem().shouldBe(USD)
    }
  }

  test("clear removes preference from DAO") {
    fiatCurrencyDao.storeFiatCurrencies(listOf(GBP))
    fiatCurrencyPreferenceDao.setFiatCurrencyPreference(GBP)

    fiatCurrencyPreferenceDao.fiatCurrencyPreference().test {
      awaitItem().shouldBe(GBP) // initial default value.

      repository.clear()

      awaitItem().shouldBeNull()
    }
  }
})
