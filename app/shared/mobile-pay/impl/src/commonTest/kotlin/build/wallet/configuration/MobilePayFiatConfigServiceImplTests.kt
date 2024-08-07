package build.wallet.configuration

import app.cash.turbine.test
import build.wallet.configuration.MobilePayFiatConfig.SnapTolerance
import build.wallet.keybox.config.TemplateFullAccountConfigDaoFake
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class MobilePayFiatConfigServiceImplTests : FunSpec({

  coroutineTestScope = true

  val mobilePayFiatConfigRepository = MobilePayFiatConfigRepositoryFake()
  val templateFullAccountConfigDao = TemplateFullAccountConfigDaoFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()

  val service = MobilePayFiatConfigServiceImpl(
    mobilePayFiatConfigRepository = mobilePayFiatConfigRepository,
    templateFullAccountConfigDao = templateFullAccountConfigDao,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
  )

  beforeTest {
    mobilePayFiatConfigRepository.reset()
    templateFullAccountConfigDao.reset()
    fiatCurrencyPreferenceRepository.reset()
  }

  test("use default USD config when sync has not started") {
    service.config.value.shouldBe(MobilePayFiatConfig.USD)
  }

  test("use hardcoded config for preferred currency when repository does not provide one") {
    // initiate the sync
    backgroundScope.launch {
      service.executeWork()
    }

    service.config.test {
      awaitItem() // default config

      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = EUR
      // repository does not provide a config for EUR, so we should use the hardcoded with defaults
      awaitItem().shouldBe(
        MobilePayFiatConfig(
          minimumLimit = FiatMoney(EUR, 0.toBigDecimal()),
          maximumLimit = FiatMoney(EUR, 200.toBigDecimal()),
          snapValues = emptyMap()
        )
      )
    }
  }

  test("use config provided by f8e") {
    val eurConfig = MobilePayFiatConfig(
      minimumLimit = FiatMoney(EUR, 10.toBigDecimal()),
      maximumLimit = FiatMoney(EUR, 100.toBigDecimal()),
      snapValues = mapOf(
        FiatMoney(EUR, 20.00.toBigDecimal()) to SnapTolerance(FiatMoney(EUR, 2.00.toBigDecimal()))
      )
    )

    // initiate the sync
    backgroundScope.launch {
      service.executeWork()
    }

    service.config.test {
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = EUR
      awaitItem() // hardcoded config

      // repository provides a config for EUR
      mobilePayFiatConfigRepository.configs.value = mapOf(EUR to eurConfig)
      awaitItem().shouldBe(eurConfig)
    }
  }

  test("switch preferred currency when multiple configs are available") {
    val eurConfig = MobilePayFiatConfig(
      minimumLimit = FiatMoney(EUR, 10.toBigDecimal()),
      maximumLimit = FiatMoney(EUR, 100.toBigDecimal()),
      snapValues = mapOf(
        FiatMoney(EUR, 20.00.toBigDecimal()) to SnapTolerance(FiatMoney(EUR, 2.00.toBigDecimal()))
      )
    )
    val gbpConfig = MobilePayFiatConfig(
      minimumLimit = FiatMoney(GBP, 20.toBigDecimal()),
      maximumLimit = FiatMoney(GBP, 200.toBigDecimal()),
      snapValues = mapOf(
        FiatMoney(GBP, 50.00.toBigDecimal()) to SnapTolerance(FiatMoney(GBP, 5.00.toBigDecimal()))
      )
    )
    mobilePayFiatConfigRepository.configs.value = mapOf(
      EUR to eurConfig,
      GBP to gbpConfig
    )

    // initiate the sync
    backgroundScope.launch {
      service.executeWork()
    }

    service.config.test {
      // use config based on the preferred currency
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = EUR
      awaitItem().shouldBe(eurConfig)

      // switch to GBP
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = GBP

      // use config based on the new preferred currency
      awaitItem().shouldBe(gbpConfig)
    }
  }
})
