package build.wallet.chaincode.delegation

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.core.coreFfiNetwork
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.serverAccountDpub as coreServerAccountDpub

@BitkeyInject(AppScope::class)
class ChaincodeDelegationServerDpubGeneratorImpl : ChaincodeDelegationServerDpubGenerator {
  override fun generate(
    network: BitcoinNetworkType,
    serverRootPublicKey: String,
  ): String = coreServerAccountDpub(network.coreFfiNetwork, serverRootPublicKey)
}
