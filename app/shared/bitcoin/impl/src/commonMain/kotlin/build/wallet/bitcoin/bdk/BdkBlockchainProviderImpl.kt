package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkBlockchainConfig
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkElectrumConfig
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.blockchain
import build.wallet.bdk.bindings.toBdkResult
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

class BdkBlockchainProviderImpl(
  private val bdkBlockchainFactory: BdkBlockchainFactory,
  private val electrumServerSettingProvider: ElectrumServerSettingProvider,
) : BdkBlockchainProvider {
  private var blockchainCache: BdkBlockchainHolder? = null

  override suspend fun blockchain(electrumServer: ElectrumServer?): BdkResult<BdkBlockchain> {
    val electrumServerToUse = electrumServer ?: electrumServerSettingProvider.get().first().server
    log(
      level = Debug
    ) { "Getting Electrum server ${electrumServerToUse.electrumServerDetails.url()}" }

    return when (val cached = blockchainCache) {
      null -> updateCachedBlockchain(electrumServerToUse)
      else -> {
        when (cached.electrumServer) {
          electrumServerToUse -> BdkResult.Ok(cached)
          else -> updateCachedBlockchain(electrumServerToUse)
        }
      }
    }.result.map { it.bdkBlockchain }.toBdkResult()
  }

  private suspend fun updateCachedBlockchain(
    electrumServer: ElectrumServer,
  ): BdkResult<BdkBlockchainHolder> {
    return getBlockchain(electrumServer).also {
      blockchainCache = it.get()
    }
  }

  override suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration,
  ): BdkResult<BdkBlockchainHolder> {
    return bdkBlockchainFactory
      .blockchain(
        config =
          BdkBlockchainConfig.Electrum(
            config =
              BdkElectrumConfig(
                url = electrumServer.electrumServerDetails.url(),
                socks5 = null,
                retry = 5,
                timeout = timeout.inWholeSeconds.toUInt(),
                stopGap = 1000,
                validateDomain = true
              )
          )
      )
      .result
      .logFailure(Warn) {
        "Error establishing BDK blockchain connection"
      }
      .map { BdkBlockchainHolder(electrumServer, it) }
      .toBdkResult()
  }

  fun reset() {
    blockchainCache = null
  }
}
