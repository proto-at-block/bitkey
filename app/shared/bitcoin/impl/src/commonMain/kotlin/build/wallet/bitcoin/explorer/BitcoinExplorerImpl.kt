package build.wallet.bitcoin.explorer

import build.wallet.bitcoin.BitcoinNetworkType
import io.ktor.http.*

class BitcoinExplorerImpl : BitcoinExplorer {
  override fun getTransactionUrl(
    txId: String,
    network: BitcoinNetworkType,
    explorerType: BitcoinExplorerType,
    vout: Int?,
  ): String {
    return when (explorerType) {
      BitcoinExplorerType.Mempool -> {
        val networkTypePath =
          when (network) {
            BitcoinNetworkType.BITCOIN -> ""
            BitcoinNetworkType.TESTNET -> "testnet/"
            BitcoinNetworkType.SIGNET -> "signet/"
            BitcoinNetworkType.REGTEST -> "regtest/"
          }

        URLBuilder("https://mempool.space/${networkTypePath}tx/$txId")
          .apply {
            if (vout != null) {
              // Add output index as a URL anchor to highlight the output in the Mempool explorer.
              fragment = "vout=$vout"
            }
          }
          .buildString()
      }
    }
  }
}
