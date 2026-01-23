package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Factory for creating legacy BDK blockchain instances used specifically for wallet sync.
 *
 * This is separate from [BdkBlockchainFactory] which creates BDK 2 blockchains for
 * other operations (broadcast, fee estimation, etc.).
 *
 * Uses the legacy BDK FFI, which is required until wallet sync is migrated to BDK 2.
 */
interface LegacyBdkBlockchainFactory {
  fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain>
}

suspend fun LegacyBdkBlockchainFactory.blockchain(
  config: BdkBlockchainConfig,
): BdkResult<BdkBlockchain> {
  return withContext(Dispatchers.BdkIO) {
    blockchainBlocking(config)
  }
}
