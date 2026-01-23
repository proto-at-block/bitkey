package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstraction over blockchain client for Bitcoin operations.
 *
 * BDK 2 implementation uses ElectrumClient under the hood.
 *
 * @see <a href="https://github.com/bitcoindevkit/bdk-ffi/blob/v0.32.1/bdk-ffi/src/bdk.udl#L166">Legacy BDK Blockchain</a>
 * @see <a href="https://github.com/bitcoindevkit/bdk/blob/electrum-0.23.2/crates/electrum/src/bdk_electrum_client.rs#L18">BDK 2 BdkElectrumClient</a>
 */
interface BdkBlockchain {
  /**
   * Broadcasts a transaction to the network.
   * @return the transaction ID of the broadcast transaction
   */
  fun broadcastBlocking(transaction: BdkTransaction): BdkResult<String>

  /**
   * Gets the current blockchain height.
   */
  fun getHeightBlocking(): BdkResult<Long>

  /**
   * Gets the block hash at the specified height.
   */
  fun getBlockHashBlocking(height: Long): BdkResult<String>

  /**
   * Estimates the fee rate in sats/vB for confirmation within target blocks.
   */
  fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float>

  /**
   * Fetches a transaction by its txid.
   */
  fun getTxBlocking(txid: String): BdkResult<BdkTransaction>
}

suspend fun BdkBlockchain.broadcast(transaction: BdkTransaction): BdkResult<String> {
  return withContext(Dispatchers.BdkIO) {
    broadcastBlocking(transaction)
  }
}

suspend fun BdkBlockchain.getHeight(): BdkResult<Long> {
  return withContext(Dispatchers.BdkIO) {
    getHeightBlocking()
  }
}

suspend fun BdkBlockchain.getBlockHash(height: Long): BdkResult<String> {
  return withContext(Dispatchers.BdkIO) {
    getBlockHashBlocking(height)
  }
}

suspend fun BdkBlockchain.estimateFee(targetBlocks: ULong): BdkResult<Float> {
  return withContext(Dispatchers.BdkIO) {
    estimateFeeBlocking(targetBlocks)
  }
}

suspend fun BdkBlockchain.getTx(txid: String): BdkResult<BdkTransaction> {
  return withContext(Dispatchers.BdkIO) {
    getTxBlocking(txid)
  }
}
