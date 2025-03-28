package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*

/**
 * This extension function here allows us to point to different Mempool Electrum servers based on
 * the `BitcoinNetworkType`.
 *
 * Specifically, this is mostly used to determine which Electrum node we want users to connect to,
 * if they did not specify one in settings.
 */
fun BitcoinNetworkType.mempoolElectrumServerDetails(
  isAndroidEmulator: Boolean,
): ElectrumServerDetails {
  return when (this) {
    BITCOIN ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "bitkey.mempool.space",
        port = "50002"
      )
    SIGNET ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "bitkey.mempool.space",
        port = "60602"
      )
    TESTNET ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "bitkey.mempool.space",
        port = "60002"
      )
    REGTEST ->
      ElectrumServerDetails(
        protocol = "tcp",
        // Android emulator puts the host device's localhost IP at 10.0.2.2
        // https://developer.android.com/studio/run/emulator-networking
        host = when {
          isAndroidEmulator -> "10.0.2.2"
          else -> "localhost"
        },
        port = "8101"
      )
    else -> error("not supported")
  }
}

/**
 * This extension function here allows us to point to different Blockstream Electrum servers based
 * on the `BitcoinNetworkType`.
 *
 * Specifically, this is mostly used to determine which Electrum node we want users to connect to,
 * if they did not specify one in settings.
 */
fun BitcoinNetworkType.blockstreamElectrumServerDetails(
  isAndroidEmulator: Boolean,
): ElectrumServerDetails {
  return when (this) {
    BITCOIN ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "electrum.blockstream.info",
        port = "50002"
      )
    // We use Mempool here since Blockstream does not host a public Signet Electrum server.
    SIGNET ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "bitkey.mempool.space",
        port = "60602"
      )
    TESTNET ->
      ElectrumServerDetails(
        protocol = "ssl",
        host = "electrum.blockstream.info",
        port = "60002"
      )
    REGTEST ->
      ElectrumServerDetails(
        protocol = "tcp",
        // Android emulator puts the host device's localhost IP at 10.0.2.2
        // https://developer.android.com/studio/run/emulator-networking
        host = when {
          isAndroidEmulator -> "10.0.2.2"
          else -> "localhost"
        },
        port = "8101"
      )
    else -> error("not supported")
  }
}

/**
 * This extension function maps the bitcoin network variant with its corresponding genesis block
 * hash.
 */
fun BitcoinNetworkType.chainHash(): String {
  return when (this) {
    // https://blockstream.info/block/000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f
    // https://mempool.space/block/000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f
    BITCOIN -> "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"
    // https://mempool.space/signet/block/00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6
    SIGNET -> "00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6"
    // https://blockstream.info/testnet/block/000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943
    // https://mempool.space/testnet/block/000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943
    TESTNET -> "000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"
    else -> error("not supported")
  }
}
