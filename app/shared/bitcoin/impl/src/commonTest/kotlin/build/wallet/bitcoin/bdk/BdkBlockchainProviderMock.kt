package build.wallet.bitcoin.bdk

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkBlockchainMock
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkResult.Ok
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.chainHash
import kotlin.time.Duration

class BdkBlockchainProviderMock(
  turbine: (String) -> Turbine<Any?>,
  var blockchainResult: BdkResult<BdkBlockchain> = Ok(BdkBlockchainMock),
  var getBlockchainResult: BdkResult<BdkBlockchainHolder> =
    Ok(
      BdkBlockchainHolder(
        electrumServer = ElectrumServerMock,
        bdkBlockchain = BdkBlockchainMock
      )
    ),
) : BdkBlockchainProvider {
  companion object {
    val BdkBlockchainMock =
      BdkBlockchainMock(
        blockHeightResult = Ok(1),
        blockHashResult = Ok(BitcoinNetworkType.BITCOIN.chainHash()),
        broadcastResult = Ok(Unit)
      )

    val ElectrumServerMock =
      F8eDefined(
        ElectrumServerDetails(
          host = "one.info",
          port = "50002"
        )
      )
  }

  val blockchainCalls = turbine("blockchain calls")

  override suspend fun blockchain(electrumServer: ElectrumServer?): BdkResult<BdkBlockchain> {
    blockchainCalls += electrumServer
    return blockchainResult
  }

  override suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration,
  ) = getBlockchainResult

  fun reset() {
    blockchainResult = Ok(BdkBlockchainMock)
    getBlockchainResult =
      Ok(
        BdkBlockchainHolder(
          electrumServer = ElectrumServerMock,
          bdkBlockchain = BdkBlockchainMock
        )
      )
  }
}
