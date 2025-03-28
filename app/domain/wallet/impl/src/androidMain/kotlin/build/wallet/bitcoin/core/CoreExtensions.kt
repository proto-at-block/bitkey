package build.wallet.bitcoin.core

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.rust.core.FfiNetwork.BITCOIN
import build.wallet.rust.core.FfiNetwork.REGTEST
import build.wallet.rust.core.FfiNetwork.SIGNET
import build.wallet.rust.core.FfiNetwork.TESTNET

typealias CoreFfiNetwork = build.wallet.rust.core.FfiNetwork

internal val BitcoinNetworkType.coreFfiNetwork: CoreFfiNetwork
  get() =
    when (this) {
      BitcoinNetworkType.BITCOIN -> BITCOIN
      BitcoinNetworkType.SIGNET -> SIGNET
      BitcoinNetworkType.TESTNET -> TESTNET
      BitcoinNetworkType.REGTEST -> REGTEST
    }
