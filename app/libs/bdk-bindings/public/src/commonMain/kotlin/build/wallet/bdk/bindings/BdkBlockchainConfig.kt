package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L159
 */
// Kept as a sealed class for Swift interop: iOS sources match nested subclasses
// (e.g. `BdkBlockchainConfig.Electrum`), whose Swift nesting is lost if this becomes an interface.
@Suppress("AbstractClassCanBeInterface")
sealed class BdkBlockchainConfig {
  data class Electrum(
    val config: BdkElectrumConfig,
  ) : BdkBlockchainConfig()
}
