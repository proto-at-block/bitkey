package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*

class BdkBumpFeeTxBuilderMock(
  var txid: String = "abc",
  var finishOutputs: List<BdkTxOut> = emptyList(),
  var allowShrinkingScript: BdkScript? = null,
  var finishFeeAmount: ULong = 3442UL,
) : BdkBumpFeeTxBuilder {
  override fun enableRbf(): BdkBumpFeeTxBuilder {
    return this
  }

  override fun allowShrinking(script: BdkScript): BdkBumpFeeTxBuilder {
    this.allowShrinkingScript = script
    return this
  }

  override fun finish(wallet: BdkWallet): BdkResult<BdkPartiallySignedTransaction> {
    return BdkResult.Ok(
      BdkPartiallySignedTransactionMock(
        txId = txid,
        extractTxResult =
          BdkTransactionMock(
            output = finishOutputs
          ),
        feeAmount = finishFeeAmount
      )
    )
  }

  fun reset() {
    txid = "abc"
    finishOutputs = emptyList()
    allowShrinkingScript = null
    finishFeeAmount = 3442UL
  }
}
