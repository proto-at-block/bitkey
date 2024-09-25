package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*
import com.ionspin.kotlin.bignum.integer.BigInteger

class BdkTxBuilderMock(
  var finishOutputs: List<BdkTxOut> = emptyList(),
  var finishFeeAmount: ULong = 3442UL,
) : BdkTxBuilder {
  override fun addRecipient(
    script: BdkScript,
    amount: BigInteger,
  ): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun feeRate(satPerVbyte: Float): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun feeAbsolute(fee: Long): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun addUtxos(utxos: List<BdkOutPoint>): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun drainTo(address: BdkAddress): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun drainWallet(): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun enableRbf(): BdkTxBuilder {
    return BdkTxBuilderMock(
      finishOutputs = finishOutputs
    )
  }

  override fun finish(wallet: BdkWallet): BdkResult<BdkTxBuilderResult> {
    val fakePsbt =
      BdkPartiallySignedTransactionMock(
        extractTxResult =
          BdkTransactionMock(
            output = finishOutputs
          ),
        feeAmount = finishFeeAmount
      )
    return BdkResult.Ok(BdkTxBuilderResultMock(fakePsbt))
  }

  fun reset() {
    finishOutputs = listOf()
  }
}
