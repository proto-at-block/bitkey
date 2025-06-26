package build.wallet.f8e.serialization

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*

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
internal fun BitcoinNetworkType.Companion.fromJsonString(value: String): BitcoinNetworkType {
  return when (value) {
    "bitcoin-regtest" -> REGTEST // TODO: remove this edge case once W-11495 is fixed
    else -> BitcoinNetworkType.valueOf(value.uppercase())
  }
}
