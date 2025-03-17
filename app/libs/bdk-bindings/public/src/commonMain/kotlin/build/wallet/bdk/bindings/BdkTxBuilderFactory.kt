package build.wallet.bdk.bindings

interface BdkTxBuilderFactory {
  fun txBuilder(): BdkTxBuilder
}
