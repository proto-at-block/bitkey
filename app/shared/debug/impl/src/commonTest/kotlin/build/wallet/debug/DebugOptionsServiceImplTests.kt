package build.wallet.debug

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment.*
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class DebugOptionsServiceImplTests : FunSpec({
  coroutineTestScope = true

  lateinit var debugOptionsService: DebugOptionsServiceImpl
  val sqlDriverFactory = inMemorySqlDriver().factory
  val defaultDebugOptionsDecider = DefaultDebugOptionsDeciderImpl(AppVariant.Customer)

  beforeTest {
    debugOptionsService = DebugOptionsServiceImpl(
      databaseProvider = BitkeyDatabaseProviderImpl(sqlDriverFactory),
      defaultDebugOptionsDecider = defaultDebugOptionsDecider
    )
  }

  test("default options") {
    debugOptionsService.options().test {
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set bitcoin network type") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setBitcoinNetworkType(REGTEST)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = REGTEST,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )

      debugOptionsService.setBitcoinNetworkType(TESTNET)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = TESTNET,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set hardware fake") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setIsHardwareFake(true)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = true,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )

      debugOptionsService.setIsHardwareFake(false)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set test account") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setIsTestAccount(true)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = true,
          isUsingSocRecFakes = false
        )
      )

      debugOptionsService.setIsTestAccount(false)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set using soc rec fakes") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setUsingSocRecFakes(true)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = true
        )
      )

      debugOptionsService.setUsingSocRecFakes(false)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set f8e environment") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setF8eEnvironment(Local)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Local,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )

      debugOptionsService.setF8eEnvironment(Development)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Development,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )
      )
    }
  }

  test("set delay notify duration") {
    debugOptionsService.options().test {
      awaitItem() // default options

      debugOptionsService.setDelayNotifyDuration(20.seconds)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds
        )
      )

      debugOptionsService.setDelayNotifyDuration(30.seconds)
      awaitItem().shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 30.seconds
        )
      )
    }
  }
})
