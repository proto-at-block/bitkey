package build.wallet.bdk

import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkTxBuilderResult

internal class BdkTxBuilderResultImpl(
  private val ffiTxBuilderResult: FfiTxBuilderResult,
) : BdkTxBuilderResult {
  override val psbt: BdkPartiallySignedTransaction
    get() = BdkPartiallySignedTransactionImpl(ffiTxBuilderResult.psbt)

  override fun destroy() {
    ffiTxBuilderResult.destroy()
  }
}
