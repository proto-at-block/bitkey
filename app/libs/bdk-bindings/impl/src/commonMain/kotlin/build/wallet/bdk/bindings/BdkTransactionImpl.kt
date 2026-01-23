package build.wallet.bdk.bindings

import uniffi.bdk.Script
import uniffi.bdk.Transaction

/**
 * BDK 2 implementation of [BdkTransaction] that wraps the Gobley-generated [Transaction].
 */
internal class BdkTransactionImpl(
  private val ffiTransaction: Transaction,
) : BdkTransaction {
  override fun txid(): String = ffiTransaction.computeTxid().toString()

  override fun serialize(): List<UByte> = ffiTransaction.serialize().map { it.toUByte() }

  override fun weight(): ULong = ffiTransaction.weight()

  override fun size(): ULong = ffiTransaction.totalSize()

  override fun vsize(): ULong = ffiTransaction.vsize()

  override fun input(): List<BdkTxIn> =
    ffiTransaction.input().map { txIn ->
      BdkTxIn(
        outpoint = BdkOutPoint(
          txid = txIn.previousOutput.txid.toString(),
          vout = txIn.previousOutput.vout
        ),
        sequence = txIn.sequence,
        witness = txIn.witness.map { bytes -> bytes.map { it.toUByte() } }
      )
    }

  override fun output(): List<BdkTxOut> =
    ffiTransaction.output().map { txOut ->
      BdkTxOut(
        value = txOut.value.toSat(),
        scriptPubkey = BdkScriptImpl(txOut.scriptPubkey)
      )
    }
}

/**
 * BDK 2 implementation of [BdkScript] that wraps the Gobley-generated Script.
 */
internal class BdkScriptImpl(
  private val ffiScript: Script,
) : BdkScript {
  override val rawOutputScript: List<UByte>
    get() = ffiScript.toBytes().map { it.toUByte() }
}
