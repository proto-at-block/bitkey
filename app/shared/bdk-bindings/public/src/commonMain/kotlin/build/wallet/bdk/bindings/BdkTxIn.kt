package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/ffd5a96ee00f46741561e934ce8d333f55ed4338/bdk-ffi/src/bdk.udl#L191
 */
data class BdkTxIn(
  val sequence: UInt,
  val witness: List<List<UByte>>,
)
