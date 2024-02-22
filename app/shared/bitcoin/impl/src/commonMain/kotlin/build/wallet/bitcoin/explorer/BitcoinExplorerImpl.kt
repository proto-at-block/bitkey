package build.wallet.bitcoin.explorer

import build.wallet.bitcoin.BitcoinNetworkType

class BitcoinExplorerImpl : BitcoinExplorer {
  override fun getTransactionUrl(
    txId: String,
    network: BitcoinNetworkType,
    explorerType: BitcoinExplorerType,
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

        "https://mempool.space/${networkTypePath}tx/$txId"
      }
    }
  }
}
