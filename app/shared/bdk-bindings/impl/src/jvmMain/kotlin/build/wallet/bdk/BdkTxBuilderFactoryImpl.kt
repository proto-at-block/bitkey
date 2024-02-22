package build.wallet.bdk

import build.wallet.bdk.bindings.BdkTxBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory

class BdkTxBuilderFactoryImpl : BdkTxBuilderFactory {
  override fun txBuilder(): BdkTxBuilder = BdkTxBuilderImpl(ffiTxBuilder = FfiTxBuilder())
}
