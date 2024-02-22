package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilder
import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionMock
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkTransactionMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkWallet

class BdkBumpFeeTxBuilderMock(
  var txid: String = "abc",
  var finishOutputs: List<BdkTxOut> = emptyList(),
  var finishFeeAmount: ULong = 3442UL,
) : BdkBumpFeeTxBuilder {
  override fun enableRbf(): BdkBumpFeeTxBuilder {
    return BdkBumpFeeTxBuilderMock(
      finishOutputs = finishOutputs
    )
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
}
