package build.wallet.bdk.bindings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import uniffi.bdk.ElectrumClient

/**
 * BDK 2 implementation of [BdkBlockchainFactory] that creates [BdkBlockchainImpl]
 * instances backed by [ElectrumClient] from Gobley/UniFFI bindings.
 */
@BitkeyInject(AppScope::class)
public class BdkBlockchainFactoryImpl : BdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> {
    return when (config) {
      is BdkBlockchainConfig.Electrum -> {
        runCatchingElectrum {
          val electrumClient = ElectrumClient(
            url = config.config.url,
            socks5 = config.config.socks5
          )
          BdkBlockchainImpl(BdkElectrumClientImpl(electrumClient))
        }
      }
    }
  }
}
