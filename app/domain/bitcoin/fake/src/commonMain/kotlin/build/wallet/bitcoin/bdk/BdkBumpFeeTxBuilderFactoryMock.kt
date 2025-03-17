package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilder
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory

class BdkBumpFeeTxBuilderFactoryMock(
  private val txBuilder: BdkBumpFeeTxBuilder,
) : BdkBumpFeeTxBuilderFactory {
  override fun bumpFeeTxBuilder(
    txid: String,
    feeRate: Float,
  ): BdkBumpFeeTxBuilder {
    return txBuilder
  }
}
