package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L388
 */
interface BdkDescriptorSecretKey {
  fun derive(path: BdkDerivationPath): BdkResult<BdkDescriptorSecretKey>

  fun extend(path: BdkDerivationPath): BdkResult<BdkDescriptorSecretKey>

  fun asPublic(): BdkDescriptorPublicKey

  /**
   * Returns "xprv" for this descriptor key.
   *
   * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L320
   *
   * Using `raw` name instead of `asString()` so that caller do not confuse with Kotlin's
   * `Any#toString()`.
   */
  fun raw(): String

  fun secretBytes(): ByteArray
}
