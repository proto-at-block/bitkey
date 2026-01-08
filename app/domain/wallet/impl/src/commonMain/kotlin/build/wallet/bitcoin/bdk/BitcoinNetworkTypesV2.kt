package build.wallet.bitcoin.bdk

import build.wallet.bitcoin.BitcoinNetworkType
import uniffi.bdk.Network

/**
 * Maps our public [BitcoinNetworkType] to BDK v2's [Network] type.
 */
val BitcoinNetworkType.bdkNetworkV2: Network
  get() =
    when (this) {
      BitcoinNetworkType.BITCOIN -> Network.BITCOIN
      BitcoinNetworkType.TESTNET -> Network.TESTNET
      BitcoinNetworkType.SIGNET -> Network.SIGNET
      BitcoinNetworkType.REGTEST -> Network.REGTEST
    }
