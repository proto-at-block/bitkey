@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.money.currency

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClientFake
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FiatCurrenciesServiceImplTests : FunSpec({

  coroutineTestScope = true

  val fiatCurrencyDefinitionF8eClient = FiatCurrencyDefinitionF8eClientFake()
  val debugOptionsService = DebugOptionsServiceFake()
  lateinit var fiatCurrencyDao: FiatCurrencyDao
  lateinit var service: FiatCurrenciesServiceImpl

  beforeTest {
    fiatCurrencyDefinitionF8eClient.reset()
    debugOptionsService.reset()

    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

    fiatCurrencyDao = FiatCurrencyDaoImpl(databaseProvider)
    service = FiatCurrenciesServiceImpl(
      fiatCurrencyDao = fiatCurrencyDao,
      fiatCurrencyDefinitionF8eClient = fiatCurrencyDefinitionF8eClient,
      debugOptionsService = debugOptionsService
    )
  }

  test("USD is emitted as default currency") {
    service.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD))
    }
  }

  test("currencies are updated from the server") {
    backgroundScope.launch {
      service.executeWork()
    }

    service.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD)) // default

      // Server returns currencies
      fiatCurrencyDefinitionF8eClient.currencies.value = listOf(USD, EUR)

      awaitItem().shouldBe(listOf(USD, EUR))
      // Database is updated
      fiatCurrencyDao.allFiatCurrencies().first().shouldBe(listOf(USD, EUR))
    }
  }

  test("server error is ignored") {
    backgroundScope.launch {
      service.executeWork()
    }

    service.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD)) // default

      // Server returns error
      fiatCurrencyDefinitionF8eClient.networkError = NetworkError(Error())

      expectNoEvents()
    }
  }

  test("currencies from database are synced into cache") {
    backgroundScope.launch {
      service.executeWork()
    }

    service.allFiatCurrencies.test {
      awaitItem().shouldBe(listOf(USD))

      // override database values
      fiatCurrencyDao.storeFiatCurrencies(listOf(USD, EUR))

      awaitItem().shouldBe(listOf(USD, EUR))
    }
  }
})
