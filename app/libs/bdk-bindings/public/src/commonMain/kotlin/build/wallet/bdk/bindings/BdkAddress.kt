package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L447
 */
interface BdkAddress {
  fun asString(): String

  fun scriptPubkey(): BdkScript

  fun network(): BdkNetwork

  fun isValidForNetwork(network: BdkNetwork): Boolean
}
