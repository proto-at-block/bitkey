package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.*
import com.github.michaelbull.result.map

/**
 * Legacy BDK implementation of [BdkBlockchain] using the Android BDK bindings.
 * Used for all blockchain operations when [Bdk2FeatureFlag] is disabled.
 */
internal class LegacyBdkBlockchainImpl(
  internal val ffiBlockchain: FfiBlockchain,
) : BdkBlockchain {
  override fun broadcastBlocking(transaction: BdkTransaction): BdkResult<String> {
    require(transaction is BdkTransactionImpl) {
      "Legacy blockchain requires BdkTransactionImpl, got ${transaction::class.simpleName}"
    }
    return runCatchingBdkError {
      ffiBlockchain.broadcast(transaction.ffiTransaction)
      transaction.txid()
    }
  }

  override fun getHeightBlocking(): BdkResult<Long> =
    runCatchingBdkError { ffiBlockchain.getHeight().toLong() }

  override fun getBlockHashBlocking(height: Long): BdkResult<String> =
    runCatchingBdkError { ffiBlockchain.getBlockHash(height.toUInt()) }

  override fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float> =
    runCatchingBdkError { ffiBlockchain.estimateFee(targetBlocks).asSatPerVb() }

  override fun getTxBlocking(txid: String): BdkResult<BdkTransaction> =
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

/**
 * Creates a legacy blockchain for wallet sync operations.
 */
internal fun createLegacyBlockchain(
  config: BdkBlockchainConfig,
): BdkResult<LegacyBdkBlockchainImpl> =
  runCatchingBdkError {
    LegacyBdkBlockchainImpl(
      ffiBlockchain = FfiBlockchain(config = config.ffiBlockchainConfig)
    )
  }
