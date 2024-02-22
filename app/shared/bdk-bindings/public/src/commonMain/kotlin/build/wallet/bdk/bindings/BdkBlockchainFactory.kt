package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BdkBlockchainFactory {
  fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain>
}

suspend fun BdkBlockchainFactory.blockchain(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> {
  return withContext(Dispatchers.BdkIO) {
    blockchainBlocking(config)
  }
}
