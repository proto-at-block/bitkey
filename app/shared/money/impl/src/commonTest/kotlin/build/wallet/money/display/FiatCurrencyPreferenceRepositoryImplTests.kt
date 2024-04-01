package build.wallet.money.display

import build.wallet.coroutines.turbine.turbines
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrencyDaoMock
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.platform.settings.LocaleCurrencyCodeProviderMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FiatCurrencyPreferenceRepositoryImplTests : FunSpec({

  val fiatCurrencyDao = FiatCurrencyDaoMock(turbines::create)
  val fiatCurrencyPreferenceDao = FiatCurrencyPreferenceDaoMock(turbines::create)
  val localeCurrencyCodeProvider = LocaleCurrencyCodeProviderMock()

  lateinit var repository: FiatCurrencyPreferenceRepository

  beforeTest {
    fiatCurrencyDao.reset()
    fiatCurrencyPreferenceDao.reset()
    localeCurrencyCodeProvider.reset()
    repository =
      FiatCurrencyPreferenceRepositoryImpl(
        fiatCurrencyDao = fiatCurrencyDao,
        fiatCurrencyPreferenceDao = fiatCurrencyPreferenceDao,
        localeCurrencyCodeProvider = localeCurrencyCodeProvider
      )
  }

  test("defaultFiatCurrency flow defaults to USD and then locale") {
    localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "EUR"
    runTest {
      repository.launchSync(backgroundScope)
      runCurrent()

      repository.defaultFiatCurrency.value.shouldBe(USD)

      // Looking up the locale currency
      fiatCurrencyDao.fiatCurrencyForTextCodeCalls.awaitItem()
        .shouldBeTypeOf<IsoCurrencyTextCode>().code.shouldBe("EUR")

      fiatCurrencyDao.fiatCurrencyForTextCodeFlow.emit(EUR)
      repository.defaultFiatCurrency.value.shouldBe(EUR)
    }
  }

  test("defaultFiatCurrency flow defaults to USD if locale is null") {
    localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = null
    runTest {
      repository.launchSync(backgroundScope)
      runCurrent()

      repository.defaultFiatCurrency.value.shouldBe(USD)

      fiatCurrencyDao.fiatCurrencyForTextCodeCalls.expectNoEvents()
    }
  }

  test("defaultFiatCurrency flow defaults to USD if locale is unknown") {
    localeCurrencyCodeProvider.localeCurrencyCodeReturnValue = "XXX"
    runTest {
      repository.launchSync(backgroundScope)
      runCurrent()

      repository.defaultFiatCurrency.value.shouldBe(USD)

      // Looking up the locale currency
      fiatCurrencyDao.fiatCurrencyForTextCodeCalls.awaitItem()
        .shouldBeTypeOf<IsoCurrencyTextCode>().code.shouldBe("XXX")

      repository.defaultFiatCurrency.value.shouldBe(USD)
    }
  }

  test("fiatCurrencyPreference flow defaults to null and returns dao value") {
    runTest {
      repository.launchSync(backgroundScope)

      runCurrent()

      repository.fiatCurrencyPreference.value.shouldBe(null)
      fiatCurrencyDao.fiatCurrencyForTextCodeCalls.awaitItem()

      fiatCurrencyPreferenceDao.fiatCurrencyPreferenceFlow.emit(GBP)

      repository.fiatCurrencyPreference.value.shouldBe(GBP)
    }
  }

  test("set fiat currency preference calls dao") {
    repository.setFiatCurrencyPreference(EUR)
    fiatCurrencyPreferenceDao.setCurrencyPreferenceCalls.awaitItem()
      .shouldBe(EUR)
  }

  test("clear calls dao") {
    repository.clear()
    fiatCurrencyPreferenceDao.clearCalls.awaitItem()
  }
})
