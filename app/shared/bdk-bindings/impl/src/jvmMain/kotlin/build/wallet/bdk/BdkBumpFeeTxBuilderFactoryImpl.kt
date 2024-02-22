package build.wallet.bdk

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilder
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory

class BdkBumpFeeTxBuilderFactoryImpl : BdkBumpFeeTxBuilderFactory {
  override fun bumpFeeTxBuilder(
    txid: String,
    feeRate: Float,
  ): BdkBumpFeeTxBuilder =
    BdkBumpFeeTxBuilderImpl(ffiTxBuilder = FfiBumpFeeTxBuilder(txid, feeRate))
}
