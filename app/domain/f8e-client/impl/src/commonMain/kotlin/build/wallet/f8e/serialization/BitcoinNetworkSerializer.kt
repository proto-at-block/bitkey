package build.wallet.f8e.serialization

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET

/**
 * Encode [BitcoinNetworkType] as JSON string. F8e expects lowercase string.
 */
internal fun BitcoinNetworkType.toJsonString() =
  when (this) {
    BITCOIN -> "bitcoin"
    SIGNET -> "signet"
    TESTNET -> "testnet"
    REGTEST -> "regtest"
  }

/**
 * Decode [BitcoinNetworkType] from JSON string. F8e encodes network type as lowercase string.
 */
internal fun BitcoinNetworkType.Companion.fromJsonString(value: String) =
  BitcoinNetworkType.valueOf(value.uppercase())
