package build.wallet.bdk.bindings

import uniffi.bdk.Transaction
import uniffi.bdk.Txid

/**
 * BDK 2 implementation of [BdkBlockchain] that wraps the Gobley-generated Electrum client
 * via [BdkElectrumClient].
 *
 * Constructed by [BdkBlockchainFactoryImpl].
 */
internal class BdkBlockchainImpl(
  private val electrumClient: BdkElectrumClient,
) : BdkBlockchain {
  override fun broadcastBlocking(transaction: BdkTransaction): BdkResult<String> =
    runCatchingElectrum {
      // Convert BdkTransaction to BDK 2 Transaction via serialization
      val serializedTx = transaction.serialize().map { it.toByte() }.toByteArray()
      val ffiTransaction = Transaction(serializedTx)
      val txid = electrumClient.transactionBroadcast(ffiTransaction)
      txid.toString()
    }

  override fun getHeightBlocking(): BdkResult<Long> =
    runCatchingElectrum { electrumClient.latestBlockHeight() }

  override fun getBlockHashBlocking(height: Long): BdkResult<String> =
    runCatchingElectrum { electrumClient.blockHash(height.toULong()) }

  override fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float> =
    runCatchingElectrum {
      val btcPerKb = electrumClient.estimateFee(targetBlocks)
      val satsPerVb = btcPerKbToSatsPerVb(btcPerKb)
      if (!satsPerVb.isFinite() || satsPerVb <= 0f) {
        throw InvalidFeeRateException("Electrum returned invalid fee rate: $btcPerKb")
      }
      satsPerVb
    }

  override fun getTxBlocking(txid: String): BdkResult<BdkTransaction> =
    runCatchingElectrum {
      val ffiTxid = Txid.fromString(txid)
      val ffiTransaction = electrumClient.transactionGet(ffiTxid)
      BdkTransactionImpl(ffiTransaction)
    }
}
