package build.wallet.statemachine.data.sync

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServer.Mempool
import build.wallet.bitcoin.sync.ElectrumServerConfigRepositoryMock
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.OffElectrumServerPreferenceValueMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.configuration.ElectrumServers
import build.wallet.f8e.configuration.GetBdkConfigurationF8eClientMock
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ElectrumServerDataStateMachineImplTests : FunSpec({
  val signetElectrumServer = ElectrumServerDetails("chicken.info", "1234")
  val allElectrumServers =
    ElectrumServers(
      mainnet = F8eDefined(ElectrumServerDetails("chicken.info", "50002")),
      signet = F8eDefined(signetElectrumServer),
      testnet = F8eDefined(ElectrumServerDetails("chicken.info", "50002")),
      regtest = null
    )
  val getBdkConfigurationF8eClient =
    GetBdkConfigurationF8eClientMock(
      turbines::create,
      defaultElectrumServers = allElectrumServers
    )

  val electrumServerRepository = ElectrumServerConfigRepositoryMock(turbines::create)
  val dataStateMachine =
    ElectrumServerDataStateMachineImpl(
      electrumServerRepository = electrumServerRepository,
      getBdkConfigurationF8eClient = getBdkConfigurationF8eClient
    )

  val defaultProps =
    ElectrumServerDataProps(
      f8eEnvironment = Production,
      network = SIGNET
    )

  context("Electrum Server DB has no persisted F8e-defined Electrum server information") {
    test("should return Mempool server as a default") {
      dataStateMachine.test(defaultProps) {
        // First returns a Mempool placeholder Electrum host
        awaitItem().let {
          it.userDefinedElectrumServerPreferenceValue.shouldBe(OffElectrumServerPreferenceValueMock)
          it.defaultElectrumServer.shouldBe(Mempool(SIGNET))
        }

        // Reaches out to F8e to get configuration
        getBdkConfigurationF8eClient.getConfigurationCalls.awaitItem()
        // Updates local SQLite DB
        electrumServerRepository.storeF8eDefinedServerCalls.awaitItem()

        // Should update to new Electrum server
        awaitItem().let {
          it.defaultElectrumServer.shouldBe(F8eDefined(signetElectrumServer))
          it.userDefinedElectrumServerPreferenceValue.shouldBe(OffElectrumServerPreferenceValueMock)
        }
      }
    }
  }

  context("Electrum Server DB has persisted F8e-defined Electrum server information") {
    beforeEach {
      electrumServerRepository.f8eDefinedElectrumConfig.value = F8eDefined(signetElectrumServer)
    }

    test("should return persisted value right away, and updates latest F8e value") {
      dataStateMachine.test(defaultProps) {
        // First returns a Mempool placeholder Electrum host
        awaitItem().let {
          it.defaultElectrumServer.shouldBe(Mempool(SIGNET))
        }

        // Eventually DB emits the right value
        awaitItem().let {
          it.defaultElectrumServer.shouldBe(F8eDefined(signetElectrumServer))
        }

        // Reaches out to F8e to get configuration
        getBdkConfigurationF8eClient.getConfigurationCalls.awaitItem()
        // Updates local KV store
        electrumServerRepository.storeF8eDefinedServerCalls.awaitItem()
      }
    }
  }
})
