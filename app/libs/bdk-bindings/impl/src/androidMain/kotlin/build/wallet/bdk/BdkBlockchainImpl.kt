package build.wallet.bdk

import build.wallet.bdk.bindings.*
import com.github.michaelbull.result.map

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

  override fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float> =
    runCatchingBdkError { ffiBlockchain.estimateFee(targetBlocks).asSatPerVb() }

  override fun getTx(txid: String): BdkResult<BdkTransaction> =
    runCatchingBdkError { ffiBlockchain.getTx(txid) }
      .result
      .map { res ->
        return when (res) {
          null -> BdkResult.Err(
            BdkError.TransactionNotFound(
              cause = null,
              message = "Transaction with $txid not found."
            )
          )
          else -> BdkResult.Ok(res.bdkTransaction)
        }
      }
      .toBdkResult()
}
