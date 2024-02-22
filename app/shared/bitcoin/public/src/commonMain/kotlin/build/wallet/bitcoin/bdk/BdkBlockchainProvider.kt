package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bitcoin.sync.ElectrumServer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface BdkBlockchainProvider {
  /**
   * Creates and caches [BdkBlockchain] instance.
   */
  suspend fun blockchain(electrumServer: ElectrumServer? = null): BdkResult<BdkBlockchain>

  suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration = 5.seconds,
  ): BdkResult<BdkBlockchainHolder>
}

data class BdkBlockchainHolder(
  val electrumServer: ElectrumServer,
  val bdkBlockchain: BdkBlockchain,
)
