package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L265
 */
interface BdkTransaction {
  fun txid(): String

  fun serialize(): List<UByte>

  fun weight(): ULong

  fun size(): ULong

  fun vsize(): ULong

  fun input(): List<BdkTxIn>

  fun output(): List<BdkTxOut>
}
