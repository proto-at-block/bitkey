package build.wallet.bitcoin.explorer

import build.wallet.bitcoin.BitcoinNetworkType

class BitcoinExplorerMock : BitcoinExplorer {
  var transactionUrl: String =
    "https://mempool.space/tx/4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"

  /**
   * Creates URL to view Bitcoin transaction in a Bitcoin explorer.
   */
  override fun getTransactionUrl(
    txId: String,
    network: BitcoinNetworkType,
    explorerType: BitcoinExplorerType,
    vout: Int?,
  ): String = transactionUrl
}
