package build.wallet.bitcoin.sync

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.configuration.ElectrumServers
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClientMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class ElectrumConfigServiceImplTests : FunSpec({

  val signetElectrumServer = F8eDefined(ElectrumServerDetails("chicken.info", "1234"))
  val mainnetElectrumServer = F8eDefined(ElectrumServerDetails("chicken.info", "50002"))
  val allElectrumServers =
    ElectrumServers(
      mainnet = mainnetElectrumServer,
      signet = signetElectrumServer,
      testnet = F8eDefined(ElectrumServerDetails("chicken.info", "50002")),
      regtest = null
    )
  val getBdkConfigurationF8eClient =
    GetBdkConfigurationF8eClientMock(
      turbines::create,
      defaultElectrumServers = allElectrumServers
    )

  val electrumServerRepository = ElectrumServerConfigRepositoryFake()
  val appConfigService = AccountConfigServiceFake()

  lateinit var service: ElectrumConfigServiceImpl

  beforeEach {
    service = ElectrumConfigServiceImpl(
      electrumServerConfigRepository = electrumServerRepository,
      accountConfigService = appConfigService,
      getBdkConfigurationF8eClient = getBdkConfigurationF8eClient
    )

    electrumServerRepository.reset()
    getBdkConfigurationF8eClient.reset()
    appConfigService.reset()
  }

  test("executeWork syncs f8e electrum config") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    getBdkConfigurationF8eClient.getConfigurationCalls.awaitItem()
    electrumServerRepository.f8eDefinedElectrumConfig.test {
      awaitUntil(signetElectrumServer)
    }

    // Changing the bitcoin network should cause a resync
    appConfigService.setBitcoinNetworkType(BITCOIN)
    getBdkConfigurationF8eClient.getConfigurationCalls.awaitItem()
    electrumServerRepository.f8eDefinedElectrumConfig.test {
      awaitUntil(mainnetElectrumServer)
    }

    // Changing the f8e environment should cause a resync
    appConfigService.setF8eEnvironment(Production)
    getBdkConfigurationF8eClient.getConfigurationCalls.awaitItem()

    // Other options don't cause a resync
    appConfigService.setIsTestAccount(false)
    getBdkConfigurationF8eClient.getConfigurationCalls.expectNoEvents()
  }

  test("electrumServerPreference updates when user preference changes") {
    service.electrumServerPreference().test {
      // Initial item
      awaitItem().shouldBeNull()

      electrumServerRepository.storeUserPreference(OnElectrumServerPreferenceValueMock)
      awaitItem().shouldBe(OnElectrumServerPreferenceValueMock)
    }
  }

  test("disable custom electrum server - already Off does nothing") {
    electrumServerRepository.getUserElectrumServerPreference().test {
      awaitItem()
      service.disableCustomElectrumServer()
      expectNoEvents()
    }
  }

  test("disable custom electrum server -  previous value is On") {
    electrumServerRepository.userElectrumServerPreferenceValue.value =
      On(server = CustomElectrumServerMock)
    electrumServerRepository.getUserElectrumServerPreference().test {
      awaitItem().shouldBe(On(server = CustomElectrumServerMock))
      service.disableCustomElectrumServer()
      awaitItem().shouldBe(Off(previousUserDefinedElectrumServer = CustomElectrumServerMock))
    }
  }
})
