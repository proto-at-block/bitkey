package bitkey.account

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class DefaultAppConfigServiceImplTests : FunSpec({
  val sqlDriverFactory = inMemorySqlDriver().factory
  val accountService = AccountServiceFake()

  fun TestScope.service(appVariant: AppVariant = Customer) =
    AccountConfigServiceImpl(
      appCoroutineScope = createBackgroundScope(),
      databaseProvider = BitkeyDatabaseProviderImpl(sqlDriverFactory),
      appVariant = appVariant,
      accountService = accountService
    )

  beforeTest {
    accountService.reset()
  }

  test("activeOrDefaultConfig - config of active account is emitted") {
    val service = service(Team)

    service.activeOrDefaultConfig().test {
      awaitItem().shouldBe(service.defaultConfig().value)

      accountService.setActiveAccount(FullAccountMock)

      awaitItem().shouldBe(FullAccountMock.config)
    }
  }

  test("activeOrDefaultConfig - default config is emitted when there is no active account") {
    val service = service(Team)
    accountService.setActiveAccount(FullAccountMock)

    service.activeOrDefaultConfig().test {
      awaitUntil(FullAccountMock.config)

      accountService.clear()

      awaitItem().shouldBe(service.defaultConfig().value)
    }
  }

  test("activeOrDefaultConfig - update default config is emitted when one of the fields is changed") {
    val service = service(Team)

    service.activeOrDefaultConfig().test {
      val defaultConfig = service.defaultConfig().value
      awaitItem().shouldBe(defaultConfig)

      service.setBitcoinNetworkType(REGTEST)

      awaitItem().shouldBe(defaultConfig.copy(bitcoinNetworkType = REGTEST))
    }
  }

  test("defaultConfig - fallback config for Customer variant") {
    service(Customer).defaultConfig().test {
      awaitItem().shouldBe(
        DefaultAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
    }
  }

  test("defaultConfig - Development variant") {
    service(Development).defaultConfig().test {
      awaitItem().shouldBe(
        DefaultAccountConfig(
          bitcoinNetworkType = BitcoinNetworkType.SIGNET,
          isHardwareFake = true,
          f8eEnvironment = F8eEnvironment.Staging,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
    }
  }

  test("defaultConfig - Emergency variant") {
    service(Emergency).defaultConfig().test {
      awaitItem().shouldBe(
        DefaultAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.ForceOffline,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
    }
  }

  test("defaultConfig - Team variant") {
    service(Team).defaultConfig().test {
      awaitItem().shouldBe(
        DefaultAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.Production,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
    }
  }

  test("defaultConfig - fallback config for Beta app variant") {
    service(Beta).defaultConfig().test {
      awaitItem().shouldBe(
        DefaultAccountConfig(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
    }
  }

  test("defaultConfig - set bitcoin network type") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setBitcoinNetworkType(REGTEST)
      awaitItem().shouldBe(
        initial.copy(bitcoinNetworkType = REGTEST)
      )

      service.setBitcoinNetworkType(TESTNET)
      awaitItem().shouldBe(
        initial.copy(bitcoinNetworkType = TESTNET)
      )
    }
  }

  test("defaultConfig - set hardware fake") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setIsHardwareFake(true)
      awaitItem().shouldBe(
        initial.copy(isHardwareFake = true)
      )

      service.setIsHardwareFake(false)
      awaitItem().shouldBe(
        initial.copy(isHardwareFake = false)
      )
    }
  }

  test("defaultConfig - set test account") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setIsTestAccount(false)
      awaitItem().shouldBe(
        initial.copy(isTestAccount = false)
      )

      service.setIsTestAccount(true)
      awaitItem().shouldBe(
        initial.copy(isTestAccount = true)
      )
    }
  }

  test("defaultConfig - set using soc rec fakes") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setUsingSocRecFakes(true)
      awaitItem().shouldBe(
        initial.copy(isUsingSocRecFakes = true)
      )

      service.setUsingSocRecFakes(false)
      awaitItem().shouldBe(
        initial.copy(isUsingSocRecFakes = false)
      )
    }
  }

  test("defaultConfig - set f8e environment") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setF8eEnvironment(F8eEnvironment.Local)
      awaitItem().shouldBe(
        initial.copy(f8eEnvironment = F8eEnvironment.Local)
      )

      service.setF8eEnvironment(F8eEnvironment.Development)
      awaitItem().shouldBe(
        initial.copy(f8eEnvironment = F8eEnvironment.Development)
      )
    }
  }

  test("defaultConfig - set delay notify duration") {
    val service = service(Team)
    service.defaultConfig().test {
      val initial = awaitItem() // fallback

      service.setDelayNotifyDuration(10.seconds)
      awaitItem().shouldBe(
        initial.copy(delayNotifyDuration = 10.seconds)
      )

      service.setDelayNotifyDuration(30.seconds)
      awaitItem().shouldBe(
        initial.copy(delayNotifyDuration = 30.seconds)
      )
    }
  }

  test("defaultConfig - can't change configuration in Customer builds") {
    val service = service(Customer)

    shouldThrow<IllegalStateException> { service.setBitcoinNetworkType(REGTEST) }
    shouldThrow<IllegalStateException> { service.setIsHardwareFake(true) }
    shouldThrow<IllegalStateException> { service.setIsTestAccount(true) }
    shouldThrow<IllegalStateException> { service.setUsingSocRecFakes(true) }
    shouldThrow<IllegalStateException> { service.setF8eEnvironment(F8eEnvironment.Local) }
    shouldThrow<IllegalStateException> { service.setDelayNotifyDuration(10.seconds) }
  }
})
