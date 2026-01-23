package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkBlockchainConfig
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.LegacyBdkBlockchainFactory
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

/**
 * Factory for creating legacy BDK blockchains for wallet sync operations.
 * Uses the Android BDK bindings via FfiBlockchain.
 */
@BitkeyInject(AppScope::class)
class LegacyBdkBlockchainFactoryImpl : LegacyBdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> =
    createLegacyBlockchain(config)
}
