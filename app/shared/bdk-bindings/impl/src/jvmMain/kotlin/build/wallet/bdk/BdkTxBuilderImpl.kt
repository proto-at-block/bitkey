package build.wallet.bdk

import build.wallet.bdk.bindings.BdkAddress
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTxBuilder
import build.wallet.bdk.bindings.BdkTxBuilderResult
import build.wallet.bdk.bindings.BdkWallet
import com.ionspin.kotlin.bignum.integer.BigInteger

internal class BdkTxBuilderImpl(
  private val ffiTxBuilder: FfiTxBuilder,
) : BdkTxBuilder {
  override fun addRecipient(
    script: BdkScript,
    amount: BigInteger,
  ): BdkTxBuilder {
    require(script is BdkScriptImpl)
    return BdkTxBuilderImpl(
      ffiTxBuilder.addRecipient(
        script = script.ffiScript,
        amount = amount.ulongValue(exactRequired = true)
      )
    )
  }

  override fun feeRate(satPerVbyte: Float): BdkTxBuilder {
    return BdkTxBuilderImpl(ffiTxBuilder.feeRate(satPerVbyte))
  }

  override fun feeAbsolute(fee: Long): BdkTxBuilder {
    return BdkTxBuilderImpl(ffiTxBuilder.feeAbsolute(fee.toULong()))
  }

  override fun drainTo(address: BdkAddress): BdkTxBuilder {
    val script = address.scriptPubkey()
    require(script is BdkScriptImpl)
    return BdkTxBuilderImpl(ffiTxBuilder.drainTo(script.ffiScript))
  }

  override fun drainWallet(): BdkTxBuilder {
    return BdkTxBuilderImpl(ffiTxBuilder.drainWallet())
  }

  override fun enableRbf(): BdkTxBuilder {
    return BdkTxBuilderImpl(ffiTxBuilder.enableRbf())
  }

  override fun finish(wallet: BdkWallet): BdkResult<BdkTxBuilderResult> {
    require(wallet is BdkWalletImpl)
    return runCatchingBdkError {
      BdkTxBuilderResultImpl(ffiTxBuilderResult = ffiTxBuilder.finish(wallet.ffiWallet))
    }
  }
}
