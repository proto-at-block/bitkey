package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBlockchainMock
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult.Err
import build.wallet.bdk.bindings.BdkResult.Ok
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.DefaultElectrumServerMock
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.IncompatibleNetwork
import build.wallet.coroutines.turbine.turbines
import build.wallet.testing.shouldBeErrOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ElectrumReachabilityImplTests : FunSpec({
  val bdkBlockchainProvider = BdkBlockchainProviderMock(turbines::create)
  val electrumReachability =
    ElectrumReachabilityImpl(
      bdkBlockchainProvider = bdkBlockchainProvider
    )

  context("network-unreachable Electrum server") {
    beforeEach {
      bdkBlockchainProvider.getBlockchainResult = Err(BdkError.Electrum(null, null))
    }

    test("reachable - should return Unreachable error") {
      electrumReachability.reachable(electrumServer = DefaultElectrumServerMock, network = BITCOIN)
        .shouldBeErrOfType<ElectrumReachabilityError.Unreachable>()
    }
  }

  context("get block hash from Electrum server failure") {
    beforeEach {
      val blockchain =
        BdkBlockchainMock(
          blockHeightResult = Err(BdkError.Electrum(null, null)),
          blockHashResult = Err(BdkError.Electrum(null, null)),
          broadcastResult = Err(BdkError.Electrum(null, null)),
          feeRateResult = Err(BdkError.Electrum(null, null))
        )

      bdkBlockchainProvider.blockchainResult = Ok(blockchain)
      bdkBlockchainProvider.getBlockchainResult = Ok(BdkBlockchainHolder(DefaultElectrumServerMock, blockchain))
    }

    test("reachable - should return Unreachable error") {
      electrumReachability.reachable(electrumServer = DefaultElectrumServerMock, network = BITCOIN)
        .shouldBeErrOfType<ElectrumReachabilityError.Unreachable>()
    }
  }

  context("reachable Electrum server") {
    beforeEach {
      bdkBlockchainProvider.reset()
    }

    test("reachable - if network hashes do not match, return correct error") {
      val blockchain =
        BdkBlockchainMock(
          blockHashResult = Ok("abc"),
          broadcastResult = Err(BdkError.Electrum(null, null)),
          blockHeightResult = Err(BdkError.Electrum(null, null)),
          feeRateResult = Err(BdkError.Electrum(null, null))
        )

      bdkBlockchainProvider.blockchainResult = Ok(blockchain)
      bdkBlockchainProvider.getBlockchainResult = Ok(BdkBlockchainHolder(DefaultElectrumServerMock, blockchain))

      electrumReachability.reachable(electrumServer = DefaultElectrumServerMock, network = BITCOIN)
        .shouldBe(com.github.michaelbull.result.Err(IncompatibleNetwork))
    }

    test("reachable - if network hashes match, return Ok") {
      electrumReachability.reachable(electrumServer = DefaultElectrumServerMock, network = BITCOIN)
        .shouldBe(com.github.michaelbull.result.Ok(Unit))
    }
  }
})
