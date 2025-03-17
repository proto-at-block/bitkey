package build.wallet.bitcoin.sync

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServer.Mempool
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSetting.UserDefined
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ElectrumServerSettingProviderImplTests : FunSpec({
  val appConfigService = AccountConfigServiceFake()

  val electrumServerConfigRepositoryFake = ElectrumServerConfigRepositoryFake()

  val electrumServerSettingProviderImpl = ElectrumServerSettingProviderImpl(
    accountConfigService = appConfigService,
    electrumServerConfigRepository = electrumServerConfigRepositoryFake
  )

  beforeTest {
    appConfigService.reset()
    electrumServerConfigRepositoryFake.reset()
  }

  context("When custom Electrum server setting is set to Off") {
    context("ElectrumServerConfigDao does not have F8e-defined value") {
      test(
        "returns correct Electrum server based on debug options if an active keybox does not exist"
      ) {
        appConfigService.setBitcoinNetworkType(TESTNET)
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(Mempool(TESTNET, isAndroidEmulator = false)))
        }
      }

      test("returns correct Electrum server based on active keybox value") {
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(Mempool(SIGNET, isAndroidEmulator = false)))
        }
      }
    }

    context("ElectrumServerConfigDao has F8e-defined value") {
      val testElectrumDetails = ElectrumServerDetails(host = "chicken.info", port = "1234")

      test("returns Electrum server from persisted value") {
        electrumServerConfigRepositoryFake.f8eDefinedElectrumConfig.value =
          F8eDefined(testElectrumDetails)
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(F8eDefined(testElectrumDetails)))
        }
      }
    }
  }

  context("When custom Electrum server setting is set to On") {
    test("returns UserDefined Electrum server") {
      electrumServerConfigRepositoryFake.userElectrumServerPreferenceValue.value =
        OnElectrumServerPreferenceValueMock
      electrumServerSettingProviderImpl.get().test {
        awaitItem().shouldBe(UserDefined(CustomElectrumServerMock))
      }
    }
  }
})
