package build.wallet.bdk.bindings

import build.wallet.bdk.bindings.BdkResult.Ok

class BdkPartiallySignedTransactionBuilderMock(
  private val psbt: BdkPartiallySignedTransaction = BdkPartiallySignedTransactionMock(),
) : BdkPartiallySignedTransactionBuilder {
  override fun build(psbtBase64: String): BdkResult<BdkPartiallySignedTransaction> {
    return Ok(psbt)
  }
}
