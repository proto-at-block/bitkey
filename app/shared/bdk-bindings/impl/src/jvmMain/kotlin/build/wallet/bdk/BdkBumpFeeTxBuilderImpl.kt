package build.wallet.bdk

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilder
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkWallet

internal class BdkBumpFeeTxBuilderImpl(
  private val ffiTxBuilder: FfiBumpFeeTxBuilder,
) : BdkBumpFeeTxBuilder {
  override fun enableRbf(): BdkBumpFeeTxBuilder {
    return BdkBumpFeeTxBuilderImpl(ffiTxBuilder.enableRbf())
  }

  override fun finish(wallet: BdkWallet): BdkResult<BdkPartiallySignedTransactionImpl> {
    require(wallet is BdkWalletImpl)
    return runCatchingBdkError {
      BdkPartiallySignedTransactionImpl(ffiPsbt = ffiTxBuilder.finish(wallet.ffiWallet))
    }
  }
}
