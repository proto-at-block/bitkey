package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkTxBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory

class BdkTxBuilderFactoryMock(
  private val txBuilder: BdkTxBuilder,
) : BdkTxBuilderFactory {
  override fun txBuilder(): BdkTxBuilder {
    return txBuilder
  }
}
