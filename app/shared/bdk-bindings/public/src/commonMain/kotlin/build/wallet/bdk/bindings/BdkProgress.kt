package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L182
 */
interface BdkProgress {
  fun update(
    progress: Float,
    message: String?,
  )
}
