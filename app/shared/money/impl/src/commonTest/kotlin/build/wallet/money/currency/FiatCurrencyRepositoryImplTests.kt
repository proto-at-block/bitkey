package build.wallet.money.currency

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClientFake
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class FiatCurrencyRepositoryImplTests : FunSpec({

  coroutineTestScope = true

  lateinit var fiatCurrencyDao: FiatCurrencyDao
  val fiatCurrencyDefinitionF8eClient = FiatCurrencyDefinitionF8eClientFake()

  fun TestScope.repository(): FiatCurrencyRepositoryImpl {
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

    fiatCurrencyDao = FiatCurrencyDaoImpl(databaseProvider)

    return FiatCurrencyRepositoryImpl(
      appScope = backgroundScope,
      fiatCurrencyDao = fiatCurrencyDao,
      fiatCurrencyDefinitionF8eClient = fiatCurrencyDefinitionF8eClient
    )
  }

  beforeTest {
    fiatCurrencyDefinitionF8eClient.reset()
  }

  test("allFiatCurrencies defaults to set list then returns value from dao") {
    val repository = repository()

    repository.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD)) // default

      fiatCurrencyDao.storeFiatCurrencies(listOf(USD, EUR))

      awaitItem().shouldBe(listOf(USD, EUR))
    }
  }

  test("launch sync calls fiatCurrencyDefinitionF8eClient") {
    val repository = repository()

    repository.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD)) // default

      fiatCurrencyDefinitionF8eClient.setCurrencyDefinitions(listOf(USD, EUR))
      repository.updateFromServer(F8eEnvironment.Development)

      awaitItem().shouldBe(listOf(USD, EUR))
    }
  }
})
