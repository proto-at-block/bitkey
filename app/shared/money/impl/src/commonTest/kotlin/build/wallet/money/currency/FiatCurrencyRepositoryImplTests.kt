package build.wallet.money.currency

import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.money.FiatCurrencyDefinitionServiceMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FiatCurrencyRepositoryImplTests : FunSpec({

  val fiatCurrencyDao = FiatCurrencyDaoMock(turbines::create)
  val fiatCurrencyDefinitionService = FiatCurrencyDefinitionServiceMock(turbines::create)

  lateinit var repository: FiatCurrencyRepository

  beforeTest {
    fiatCurrencyDao.reset()
    repository =
      FiatCurrencyRepositoryImpl(
        fiatCurrencyDao = fiatCurrencyDao,
        fiatCurrencyDefinitionService = fiatCurrencyDefinitionService
      )
  }

  test("allFiatCurrencies defaults to set list then returns value from dao") {
    runTest {
      repository.launchSyncAndUpdateFromServer(
        scope = backgroundScope,
        f8eEnvironment = F8eEnvironment.Development
      )

      runCurrent()

      fiatCurrencyDefinitionService.getCurrencyDefinitionsCalls.awaitItem()
      fiatCurrencyDao.storeFiatCurrenciesCalls.awaitItem()

      // Default list
      repository.allFiatCurrencies.value.shouldBe(listOf(USD))

      // Emit new list in dao
      fiatCurrencyDao.allFiatCurrenciesFlow.emit(listOf(USD, EUR))
      repository.allFiatCurrencies.value.shouldBe(listOf(USD, EUR))
    }
  }

  test("launch sync calls fiatCurrencyDefinitionService") {
    runTest {
      repository.launchSyncAndUpdateFromServer(
        scope = backgroundScope,
        f8eEnvironment = F8eEnvironment.Development
      )

      fiatCurrencyDefinitionService.getCurrencyDefinitionsResult = Ok(listOf(USD, EUR))
      runCurrent()

      fiatCurrencyDefinitionService.getCurrencyDefinitionsCalls.awaitItem()
      fiatCurrencyDao.storeFiatCurrenciesCalls.awaitItem()
        .shouldBe(listOf(USD, EUR))
    }
  }
})
