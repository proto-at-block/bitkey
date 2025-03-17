package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L407
 */
interface BdkDescriptorPublicKey {
  /**
   * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L333
   *
   * Using `raw` name instead of `asString()` so that caller do not confuse with Kotlin's
   * `Any#toString()`.
   */
  fun raw(): String
}
