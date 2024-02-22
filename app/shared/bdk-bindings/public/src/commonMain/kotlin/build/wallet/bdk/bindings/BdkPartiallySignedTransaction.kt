package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L294
 */
interface BdkPartiallySignedTransaction {
  fun feeAmount(): ULong?

  fun txid(): String

  fun serialize(): String

  fun extractTx(): BdkTransaction
}
