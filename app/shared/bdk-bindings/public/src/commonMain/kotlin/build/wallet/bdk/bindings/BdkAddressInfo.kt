package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L51
 */
data class BdkAddressInfo(
  val index: Long,
  val address: BdkAddress,
)
