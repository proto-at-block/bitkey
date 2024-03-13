package build.wallet.bdk

import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkTransaction

/**
 * Constructed by [BdkBlockchainFactoryImpl].
 */
internal data class BdkBlockchainImpl(
  val ffiBlockchain: FfiBlockchain,
) : BdkBlockchain {
  override fun broadcastBlocking(transaction: BdkTransaction): BdkResult<Unit> {
    require(transaction is BdkTransactionImpl)
    return runCatchingBdkError { ffiBlockchain.broadcast(transaction.ffiTransaction) }
  }

  override fun getHeightBlocking(): BdkResult<Long> =
    runCatchingBdkError { ffiBlockchain.getHeight().toLong() }

  override fun getBlockHashBlocking(height: Long): BdkResult<String> =
    runCatchingBdkError { ffiBlockchain.getBlockHash(height.toUInt()) }

  override fun estimateFee(targetBlocks: ULong): BdkResult<Float> =
    runCatchingBdkError { ffiBlockchain.estimateFee(targetBlocks).asSatPerVb() }
}
