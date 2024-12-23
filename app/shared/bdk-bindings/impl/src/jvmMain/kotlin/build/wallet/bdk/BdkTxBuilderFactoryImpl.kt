package build.wallet.bdk

import build.wallet.bdk.bindings.BdkTxBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BdkTxBuilderFactoryImpl : BdkTxBuilderFactory {
  override fun txBuilder(): BdkTxBuilder = BdkTxBuilderImpl(ffiTxBuilder = FfiTxBuilder())
}
