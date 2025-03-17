package build.wallet.bitcoin.blockchain

import build.wallet.bitcoin.address.BitcoinAddress

/**
 * A blockchain that can be controlled for testing.
 */
interface BlockchainControl {
  /**
   * Generate the requested number of blocks and wait for the blocks to be indexed by the electrum
   * server. The mined bitcoin is sent to [miningWallet] and available after 100 blocks.
   *
   * @param numBlock Number of blocks to generate
   */
  suspend fun mineBlocks(
    numBlock: Int,
    mineToAddress: BitcoinAddress = blackHoleAddress,
  )

  /**
   * Only mines a block **after** the transaction is observed in the mempool.
   */
  suspend fun mineBlock(
    txid: String,
    mineToAddress: BitcoinAddress = blackHoleAddress,
  )

  companion object {
    val blackHoleAddress = BitcoinAddress("bcrt1qrt37mr0kf2th5dgsqq6k87tl8k220e7nj4ts5u")
  }
}
