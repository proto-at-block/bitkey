package build.wallet.chaincode.delegation

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.core.coreFfiNetwork
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.serverAccountDpub as coreServerAccountDpub
import build.wallet.rust.core.serverRootXpub as coreServerRootXpub

@BitkeyInject(AppScope::class)
class ChaincodeDelegationServerKeyGeneratorImpl : ChaincodeDelegationServerKeyGenerator {
  override fun generateRootExtendedPublicKey(
    network: BitcoinNetworkType,
    serverRootPublicKey: String,
  ): String = coreServerRootXpub(network.coreFfiNetwork, serverRootPublicKey)

  override fun generateAccountDescriptorPublicKey(
    network: BitcoinNetworkType,
    serverRootExtendedPublicKey: String,
  ): String = coreServerAccountDpub(network.coreFfiNetwork, serverRootExtendedPublicKey)
}
