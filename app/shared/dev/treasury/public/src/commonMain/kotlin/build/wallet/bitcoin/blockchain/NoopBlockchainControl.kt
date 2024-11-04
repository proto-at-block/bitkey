package build.wallet.bitcoin.blockchain

import build.wallet.bitcoin.address.BitcoinAddress

class NoopBlockchainControl : BlockchainControl {
  override suspend fun mineBlocks(
    numBlock: Int,
    mineToAddress: BitcoinAddress,
  ) = Unit

  override suspend fun mineBlock(
    txid: String,
    mineToAddress: BitcoinAddress,
  ) = Unit
}
