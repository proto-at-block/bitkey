package build.wallet.bitcoin.sync

import app.cash.turbine.test
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServer.Mempool
import build.wallet.bitcoin.sync.ElectrumServerSetting.Default
import build.wallet.bitcoin.sync.ElectrumServerSetting.UserDefined
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.config.TemplateKeyboxConfigDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ElectrumServerSettingProviderImplTests : FunSpec({
  val keyboxDaoMock = KeyboxDaoMock(turbines::create)
  val templateKeyboxDaoFake = TemplateKeyboxConfigDaoFake()

  val electrumServerConfigDaoMock = ElectrumServerConfigRepositoryMock(turbines::create)

  val electrumServerSettingProviderImpl =
    ElectrumServerSettingProviderImpl(
      keyboxDao = keyboxDaoMock,
      templateKeyboxConfigDao = templateKeyboxDaoFake,
      electrumServerDao = electrumServerConfigDaoMock
    )

  context("When custom Electrum server setting is set to Off") {
    context("ElectrumServerConfigDao does not have F8e-defined value") {
      test(
        "returns correct Electrum server based on template keybox if an active one does not exist"
      ) {
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(Mempool(TESTNET)))
        }
      }

      test("returns correct Electrum server based on active keybox value") {
        keyboxDaoMock.saveKeyboxAsActive(KeyboxMock)
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(Mempool(SIGNET)))
        }
      }
    }

    context("ElectrumServerConfigDao has F8e-defined value") {
      val testElectrumDetails = ElectrumServerDetails(host = "chicken.info", port = "1234")
      beforeEach {
        electrumServerConfigDaoMock.f8eDefinedElectrumConfig.value = F8eDefined(testElectrumDetails)
      }

      test("returns Electrum server from persisted value") {
        electrumServerSettingProviderImpl.get().test {
          awaitItem().shouldBe(Default(F8eDefined(testElectrumDetails)))
        }
      }
    }
  }

  context("When custom Electrum server setting is set to On") {
    beforeEach {
      electrumServerConfigDaoMock.userElectrumServerPreferenceValue.value = OnElectrumServerPreferenceValueMock
    }

    test("returns UserDefined Electrum server") {
      electrumServerSettingProviderImpl.get().test {
        awaitItem().shouldBe(UserDefined(CustomElectrumServerMock))
      }
    }
  }
})
