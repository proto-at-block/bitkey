package build.wallet.bdk

import build.wallet.bdk.bindings.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.bitcoindevkit.OutPoint

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

  override fun addUtxos(utxos: List<BdkOutPoint>): BdkTxBuilder {
    return BdkTxBuilderImpl(
      ffiTxBuilder.addUtxos(
        utxos.map { OutPoint(txid = it.txid, vout = it.vout) }
      )
    )
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

  override fun manuallySelectedOnly(): BdkTxBuilder {
    return BdkTxBuilderImpl(ffiTxBuilder.manuallySelectedOnly())
  }

  override fun finish(wallet: BdkWallet): BdkResult<BdkTxBuilderResult> {
    require(wallet is BdkWalletImpl)
    return runCatchingBdkError {
      BdkTxBuilderResultImpl(ffiTxBuilderResult = ffiTxBuilder.finish(wallet.ffiWallet))
    }
  }
}
