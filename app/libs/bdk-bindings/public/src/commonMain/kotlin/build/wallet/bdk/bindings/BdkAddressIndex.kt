package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L58
 */
sealed class BdkAddressIndex {
  data object New : BdkAddressIndex()

  data object LastUnused : BdkAddressIndex()

  data class Peek(val index: UInt) : BdkAddressIndex()
}
