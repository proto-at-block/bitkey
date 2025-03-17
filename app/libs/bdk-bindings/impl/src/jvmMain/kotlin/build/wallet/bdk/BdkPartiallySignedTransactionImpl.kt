package build.wallet.bdk

import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkTransaction

internal data class BdkPartiallySignedTransactionImpl(
  val ffiPsbt: FfiPartiallySignedTransaction,
) : BdkPartiallySignedTransaction {
  override fun feeAmount(): ULong? = ffiPsbt.feeAmount()

  override fun txid(): String = ffiPsbt.txid()

  override fun serialize(): String = ffiPsbt.serialize()

  override fun extractTx(): BdkTransaction {
    return BdkTransactionImpl(ffiPsbt.extractTx())
  }
}
