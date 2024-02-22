package build.wallet.bdk

import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkResult

class BdkPartiallySignedTransactionBuilderImpl :
  BdkPartiallySignedTransactionBuilder {
  override fun build(psbtBase64: String): BdkResult<BdkPartiallySignedTransaction> {
    return runCatchingBdkError {
      BdkPartiallySignedTransactionImpl(ffiPsbt = FfiPartiallySignedTransaction(psbtBase64))
    }
  }
}
