package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.sync.ElectrumServer
import build.wallet.bitcoin.sync.ElectrumServerSettingProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [BdkBlockchainProvider] that creates BDK blockchains for
 * fee estimation, broadcast, and other non-sync operations.
 *
 * Uses either BDK 2 or legacy BDK based on [Bdk2FeatureFlag].
 */
@BitkeyInject(AppScope::class)
class BdkBlockchainProviderImpl(
  private val bdkBlockchainFactory: BdkBlockchainFactory,
  private val legacyBdkBlockchainFactory: LegacyBdkBlockchainFactory,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
  private val electrumServerSettingProvider: ElectrumServerSettingProvider,
) : BdkBlockchainProvider {
  private var blockchainCache: CachedBlockchain? = null
  private var legacyBlockchainCache: LegacyCachedBlockchain? = null

  /**
   * Returns true if BDK 2 should be used, false for legacy BDK.
   */
  private fun useBdk2(): Boolean {
    return bdk2FeatureFlag.isEnabled()
  }

  override suspend fun blockchain(electrumServer: ElectrumServer?): BdkResult<BdkBlockchain> {
    val electrumServerToUse = electrumServer ?: electrumServerSettingProvider.get().first().server
    val useBdk2 = useBdk2()
    logDebug {
      "Getting Electrum server ${electrumServerToUse.electrumServerDetails.url()}"
    }

    return when (val cached = blockchainCache) {
      null -> updateCachedBlockchain(electrumServerToUse, useBdk2)
      else -> {
        // Invalidate cache if either the server changed or the BDK version changed
        if (cached.electrumServer == electrumServerToUse && cached.useBdk2 == useBdk2) {
          BdkResult.Ok(cached.holder)
        } else {
          updateCachedBlockchain(electrumServerToUse, useBdk2)
        }
      }
    }.result.map { it.bdkBlockchain }.toBdkResult()
  }

  override suspend fun legacyBlockchain(electrumServer: ElectrumServer?): BdkResult<BdkBlockchain> {
    val electrumServerToUse = electrumServer ?: electrumServerSettingProvider.get().first().server
    logDebug { "Getting Electrum server ${electrumServerToUse.electrumServerDetails.url()}" }

    return when (val cached = legacyBlockchainCache) {
      null -> updateLegacyCachedBlockchain(electrumServerToUse)
      else -> {
        if (cached.electrumServer == electrumServerToUse) {
          BdkResult.Ok(cached.bdkBlockchain)
        } else {
          updateLegacyCachedBlockchain(electrumServerToUse)
        }
      }
    }
  }

  private suspend fun updateLegacyCachedBlockchain(
    electrumServer: ElectrumServer,
  ): BdkResult<BdkBlockchain> {
    return getLegacyBlockchain(electrumServer).also { result ->
      result.get()?.let { blockchain ->
        legacyBlockchainCache = LegacyCachedBlockchain(electrumServer, blockchain)
      }
    }
  }

  private suspend fun getLegacyBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration = DEFAULT_TIMEOUT,
  ): BdkResult<BdkBlockchain> {
    val config = createElectrumConfig(electrumServer, timeout)

    return legacyBdkBlockchainFactory
      .blockchain(config)
      .result
      .logFailure(Warn) { "Error establishing legacy BDK blockchain connection" }
      .toBdkResult()
  }

  private suspend fun updateCachedBlockchain(
    electrumServer: ElectrumServer,
    useBdk2: Boolean,
  ): BdkResult<BdkBlockchainHolder> {
    return getBlockchain(electrumServer, useBdk2 = useBdk2).also { result ->
      result.get()?.let { holder ->
        blockchainCache = CachedBlockchain(electrumServer, useBdk2, holder)
      }
    }
  }

  override suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration,
  ): BdkResult<BdkBlockchainHolder> {
    return getBlockchain(electrumServer, timeout, useBdk2())
  }

  private suspend fun getBlockchain(
    electrumServer: ElectrumServer,
    timeout: Duration = DEFAULT_TIMEOUT,
    useBdk2: Boolean,
  ): BdkResult<BdkBlockchainHolder> {
    val config = createElectrumConfig(electrumServer, timeout)

    val blockchainResult = if (useBdk2) {
      bdkBlockchainFactory.blockchain(config)
    } else {
      legacyBdkBlockchainFactory.blockchain(config)
    }

    return blockchainResult
      .result
      .logFailure(Warn) {
        val bdkVersion = if (useBdk2) "BDK 2" else "legacy BDK"
        "Error establishing $bdkVersion blockchain connection"
      }
      .map { BdkBlockchainHolder(electrumServer, it) }
      .toBdkResult()
  }

  fun reset() {
    blockchainCache = null
    legacyBlockchainCache = null
  }

  private fun createElectrumConfig(
    electrumServer: ElectrumServer,
    timeout: Duration = DEFAULT_TIMEOUT,
  ): BdkBlockchainConfig.Electrum {
    return BdkBlockchainConfig.Electrum(
      config = BdkElectrumConfig(
        url = electrumServer.electrumServerDetails.url(),
        socks5 = null,
        retry = DEFAULT_RETRY_COUNT,
        timeout = timeout.inWholeSeconds.toUInt(),
        stopGap = DEFAULT_STOP_GAP,
        validateDomain = true
      )
    )
  }

  private companion object {
    val DEFAULT_TIMEOUT = 5.seconds
    const val DEFAULT_RETRY_COUNT = 5
    const val DEFAULT_STOP_GAP = 1000
  }
}

/**
 * Cached blockchain with metadata about what configuration was used to create it.
 * Used to invalidate cache when electrum server or BDK version changes.
 */
private data class CachedBlockchain(
  val electrumServer: ElectrumServer,
  val useBdk2: Boolean,
  val holder: BdkBlockchainHolder,
)

/**
 * Cached legacy blockchain for wallet sync operations.
 */
private data class LegacyCachedBlockchain(
  val electrumServer: ElectrumServer,
  val bdkBlockchain: BdkBlockchain,
)
