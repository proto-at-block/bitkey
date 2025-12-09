package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkTransaction
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut

internal data class BdkTransactionImpl(
  val ffiTransaction: FfiTransaction,
) : BdkTransaction {
  override fun txid(): String = ffiTransaction.txid()

  override fun serialize(): List<UByte> = ffiTransaction.serialize()

  override fun weight(): ULong = ffiTransaction.weight()

  override fun size(): ULong = ffiTransaction.size()

  override fun vsize(): ULong = ffiTransaction.vsize()

  override fun input(): List<BdkTxIn> = ffiTransaction.input().map { it.bdkTxIn }

  override fun output(): List<BdkTxOut> = ffiTransaction.output().map { it.bdkTxOut }
}
