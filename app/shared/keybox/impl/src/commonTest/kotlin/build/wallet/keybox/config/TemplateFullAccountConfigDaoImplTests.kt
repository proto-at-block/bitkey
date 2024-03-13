package build.wallet.keybox.config

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class TemplateFullAccountConfigDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()

  fun dao(appVariant: AppVariant) =
    TemplateFullAccountConfigDaoImpl(appVariant, BitkeyDatabaseProviderImpl(sqlDriver.factory))

  test("default config for Development app variant") {
    dao(appVariant = AppVariant.Development).config().test {
      awaitItem().shouldBe(
        Ok(
          FullAccountConfig(
            bitcoinNetworkType = BitcoinNetworkType.SIGNET,
            isHardwareFake = true,
            f8eEnvironment = F8eEnvironment.Staging,
            isTestAccount = true,
            isUsingSocRecFakes = false,
            delayNotifyDuration = 20.seconds
          )
        )
      )
    }
  }

  test("default config for Team app variant") {
    dao(appVariant = AppVariant.Team).config().test {
      awaitItem().shouldBe(
        Ok(
          FullAccountConfig(
            bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
            isHardwareFake = false,
            f8eEnvironment = F8eEnvironment.Production,
            isTestAccount = true,
            isUsingSocRecFakes = false,
            delayNotifyDuration = 20.seconds
          )
        )
      )
    }
  }

  test("default config for Customer app variant") {
    dao(appVariant = AppVariant.Customer).config().test {
      awaitItem().shouldBe(
        Ok(
          FullAccountConfig(
            bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
            isHardwareFake = false,
            f8eEnvironment = F8eEnvironment.Production,
            isTestAccount = false,
            isUsingSocRecFakes = false
          )
        )
      )
    }
  }

  test("update config") {
    val dao = dao(appVariant = AppVariant.Customer)
    dao.config().test {
      val initialConfig =
        FullAccountConfig(
          bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )

      awaitItem().shouldBe(Ok(initialConfig))

      val updatedConfig =
        initialConfig.copy(
          bitcoinNetworkType = BitcoinNetworkType.SIGNET,
          isHardwareFake = true,
          f8eEnvironment = F8eEnvironment.Development,
          isTestAccount = true,
          delayNotifyDuration = 20.seconds
        )
      dao.set(updatedConfig).shouldBeOk()

      awaitItem().shouldBe(Ok(updatedConfig))
    }
  }

  test("updating to same config does not emit the same config") {
    val dao = dao(appVariant = AppVariant.Customer)
    dao.config().test {
      val initialConfig =
        FullAccountConfig(
          bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = F8eEnvironment.Production,
          isTestAccount = false,
          isUsingSocRecFakes = false
        )

      awaitItem().shouldBe(Ok(initialConfig))

      dao.set(initialConfig).shouldBeOk()

      expectNoEvents()
    }
  }
})
