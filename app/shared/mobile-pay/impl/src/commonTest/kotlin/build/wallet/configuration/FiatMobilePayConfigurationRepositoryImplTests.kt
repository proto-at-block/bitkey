package build.wallet.configuration

import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.mobilepay.FiatMobilePayConfigurationServiceMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FiatMobilePayConfigurationRepositoryImplTests : FunSpec({

  val fiatMobilePayConfigurationDao = FiatMobilePayConfigurationDaoMock(turbines::create)
  val fiatMobilePayConfigurationService = FiatMobilePayConfigurationServiceMock(turbines::create)

  lateinit var repository: FiatMobilePayConfigurationRepository

  beforeTest {
    fiatMobilePayConfigurationDao.reset()
    repository =
      FiatMobilePayConfigurationRepositoryImpl(
        fiatMobilePayConfigurationDao = fiatMobilePayConfigurationDao,
        fiatMobilePayConfigurationService = fiatMobilePayConfigurationService
      )
  }

  test("fiatMobilePayConfigurationDao defaults to set list then returns value from dao") {
    runTest {
      repository.launchSyncAndUpdateFromServer(
        scope = backgroundScope,
        f8eEnvironment = F8eEnvironment.Development
      )

      runCurrent()

      fiatMobilePayConfigurationService.getFiatMobilePayConfigurationsCalls.awaitItem()
      fiatMobilePayConfigurationDao.storeConfigurationCalls.awaitItem()

      // Default list
      repository.fiatMobilePayConfigurations.value
        .mapValues { it.value.maximumLimit.value.intValue() }
        .shouldBe(mapOf(USD to 200))

      // Emit new list in dao
      fiatMobilePayConfigurationDao.allConfigurationsFlow.emit(
        mapOf(
          USD to
            FiatMobilePayConfiguration(
              minimumLimit = FiatMoney.Companion.usd(0.0),
              maximumLimit = FiatMoney.Companion.usd(300.00)
            ),
          EUR to
            FiatMobilePayConfiguration(
              minimumLimit = FiatMoney.zero(EUR),
              maximumLimit = FiatMoney(EUR, 500.toBigDecimal())
            )
        )
      )
      repository.fiatMobilePayConfigurations.value
        .mapValues { it.value.maximumLimit.value.intValue() }
        .shouldBe(
          mapOf(USD to 300, EUR to 500)
        )
    }
  }

  test("launch sync calls fiatMobilePayConfigurationService") {
    runTest {
      repository.launchSyncAndUpdateFromServer(
        scope = backgroundScope,
        f8eEnvironment = F8eEnvironment.Development
      )

      fiatMobilePayConfigurationService.getFiatMobilePayConfigurationsResult =
        Ok(
          mapOf(
            USD to
              FiatMobilePayConfiguration(
                minimumLimit = FiatMoney.Companion.usd(0.0),
                maximumLimit = FiatMoney.Companion.usd(300.00)
              ),
            EUR to
              FiatMobilePayConfiguration(
                minimumLimit = FiatMoney.zero(EUR),
                maximumLimit = FiatMoney(EUR, 500.toBigDecimal())
              )
          )
        )
      runCurrent()

      fiatMobilePayConfigurationService.getFiatMobilePayConfigurationsCalls.awaitItem()
      fiatMobilePayConfigurationDao.storeConfigurationCalls.awaitItem()
        .shouldBeInstanceOf<Map<FiatCurrency, FiatMobilePayConfiguration>>()
        .mapValues { it.value.maximumLimit.value.intValue() }
        .shouldBe(
          mapOf(USD to 300, EUR to 500)
        )
    }
  }
})
