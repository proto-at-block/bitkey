package build.wallet.bdk

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkBlockchainConfig
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkResult

class BdkBlockchainFactoryImpl : BdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> =
    runCatchingBdkError {
      BdkBlockchainImpl(
        ffiBlockchain = FfiBlockchain(config = config.ffiBlockchainConfig)
      )
    }
}
