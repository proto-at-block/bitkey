package build.wallet.bdk.bindings

class BdkTxBuilderResultMock(
  override val psbt: BdkPartiallySignedTransaction,
) : BdkTxBuilderResult {
  override fun destroy() {}
}
