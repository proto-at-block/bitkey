package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bitcoin.sync.ElectrumServer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface BdkBlockchainProvider {
  /**
   * Creates and caches [BdkBlockchain] instance.
   *
   * Uses BDK 2 or legacy BDK based on feature flag.
   */
  suspend fun blockchain(electrumServer: ElectrumServer? = null): BdkResult<BdkBlockchain>

  /**
   * Creates and caches a legacy BDK [BdkBlockchain] instance for wallet sync.
   *
   * Always uses legacy BDK regardless of feature flag because wallet sync requires
   * the platform-specific FFI Blockchain for [build.wallet.bdk.bindings.BdkWallet.syncBlocking].
   */
  suspend fun legacyBlockchain(electrumServer: ElectrumServer? = null): BdkResult<BdkBlockchain>

  suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration = 5.seconds,
  ): BdkResult<BdkBlockchainHolder>
}

data class BdkBlockchainHolder(
  val electrumServer: ElectrumServer,
  val bdkBlockchain: BdkBlockchain,
)
