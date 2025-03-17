package build.wallet.bdk.bindings

interface BdkPartiallySignedTransactionBuilder {
  fun build(psbtBase64: String): BdkResult<BdkPartiallySignedTransaction>
}
