package build.wallet.bdk.bindings

import uniffi.bdk.ElectrumClient
import uniffi.bdk.Transaction
import uniffi.bdk.Txid

internal class BdkElectrumClientImpl(
  private val delegate: ElectrumClient,
) : BdkElectrumClient {
  override fun transactionBroadcast(transaction: Transaction): Txid =
    delegate.transactionBroadcast(transaction)

  override fun latestBlockHeight(): Long = delegate.blockHeadersSubscribe().height.toLong()

  override fun blockHash(height: ULong): String = delegate.blockHash(height).toString()

  override fun estimateFee(target: ULong): Double = delegate.estimateFee(target)

  override fun transactionGet(txid: Txid): Transaction = delegate.transactionGet(txid)
}
