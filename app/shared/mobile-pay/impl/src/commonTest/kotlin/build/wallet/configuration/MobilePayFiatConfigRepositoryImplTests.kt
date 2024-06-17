package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.mobilepay.MobilePayFiatConfigF8eClientFake
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.testing.shouldBeOk
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

class MobilePayFiatConfigRepositoryImplTests : FunSpec({

  val mobilePayFiatConfigDao = MobilePayFiatConfigDaoFake()
  val mobilePayFiatConfigF8eClient = MobilePayFiatConfigF8eClientFake()

  val repository = MobilePayFiatConfigRepositoryImpl(
    mobilePayFiatConfigDao = mobilePayFiatConfigDao,
    mobilePayFiatConfigF8eClient = mobilePayFiatConfigF8eClient
  )

  beforeTest {
    mobilePayFiatConfigF8eClient.reset()
    mobilePayFiatConfigDao.reset()
  }

  test("no configurations emitted when no data in dao") {
    repository.configs.first().shouldBeEmpty()
  }

  test("fetchAndUpdateConfigs fetches configs from f8e and stores in dao") {
    mobilePayFiatConfigF8eClient.configs = mapOf(
      USD to
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.Companion.usd(0.0),
          maximumLimit = FiatMoney.Companion.usd(300.00)
        ),
      EUR to
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.zero(EUR),
          maximumLimit = FiatMoney(EUR, 500.toBigDecimal())
        )
    )

    repository
      .fetchAndUpdateConfigs(F8eEnvironment.Development)
      .shouldBeOk()

    // repository emits the fetched configurations
    repository.configs.first()
      .mapValues { it.value.maximumLimit.value.intValue() }
      .shouldBe(mapOf(USD to 300, EUR to 500))

    // dao is updated with the fetched configurations
    mobilePayFiatConfigDao.allConfigurations().first()
      .shouldBeInstanceOf<Map<FiatCurrency, MobilePayFiatConfig>>()
      .mapValues { it.value.maximumLimit.value.intValue() }
      .shouldBe(mapOf(USD to 300, EUR to 500))
  }
})
