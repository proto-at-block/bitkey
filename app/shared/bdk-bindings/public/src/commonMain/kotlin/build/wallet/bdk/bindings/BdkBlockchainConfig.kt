package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L159
 */
sealed class BdkBlockchainConfig {
  data class Electrum(
    val config: BdkElectrumConfig,
  ) : BdkBlockchainConfig()
}
