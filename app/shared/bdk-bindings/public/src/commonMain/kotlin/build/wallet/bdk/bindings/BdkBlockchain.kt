package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L165
 */
interface BdkBlockchain {
  fun broadcastBlocking(transaction: BdkTransaction): BdkResult<Unit>

  fun getHeightBlocking(): BdkResult<Long>

  fun getBlockHashBlocking(height: Long): BdkResult<String>

  fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float>

  fun getTx(txid: String): BdkResult<BdkTransaction>
}

suspend fun BdkBlockchain.broadcast(transaction: BdkTransaction): BdkResult<Unit> {
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
