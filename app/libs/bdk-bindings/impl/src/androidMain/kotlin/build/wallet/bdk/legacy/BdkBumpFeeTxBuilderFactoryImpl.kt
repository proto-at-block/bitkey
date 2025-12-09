package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilder
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BdkBumpFeeTxBuilderFactoryImpl : BdkBumpFeeTxBuilderFactory {
  override fun bumpFeeTxBuilder(
    txid: String,
    feeRate: Float,
  ): BdkBumpFeeTxBuilder =
    BdkBumpFeeTxBuilderImpl(ffiTxBuilder = FfiBumpFeeTxBuilder(txid, feeRate))
}
