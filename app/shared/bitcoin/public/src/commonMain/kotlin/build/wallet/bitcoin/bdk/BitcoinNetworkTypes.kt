package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bitcoin.BitcoinNetworkType

/**
 * Maps our public [BitcoinNetworkType] to BDK's internal [BdkNetwork] type.
 */
val BitcoinNetworkType.bdkNetwork: BdkNetwork
  get() =
    when (this) {
      BitcoinNetworkType.BITCOIN -> BdkNetwork.BITCOIN
      BitcoinNetworkType.TESTNET -> BdkNetwork.TESTNET
      BitcoinNetworkType.SIGNET -> BdkNetwork.SIGNET
      BitcoinNetworkType.REGTEST -> BdkNetwork.REGTEST
    }
