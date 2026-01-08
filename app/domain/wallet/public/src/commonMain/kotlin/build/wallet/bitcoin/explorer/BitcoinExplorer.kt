package build.wallet.bitcoin.explorer

import build.wallet.bitcoin.BitcoinNetworkType

interface BitcoinExplorer {
  /**
   * Creates URL to view Bitcoin transaction in a Bitcoin explorer.
   *
   * Example: `"https://bitkey.mempool.space/tx/4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b#vout=0"`
   *
   * @param vout a specific output index. Some explorers (like Mempool) will highlight the
   * output with this index to make it easier to see.
   */
  fun getTransactionUrl(
    txId: String,
    network: BitcoinNetworkType,
    explorerType: BitcoinExplorerType,
    vout: Int? = null,
  ): String
}
