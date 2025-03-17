package build.wallet.bdk

import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonic
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BdkDescriptorSecretKeyGeneratorImpl : BdkDescriptorSecretKeyGenerator {
  override fun generate(
    network: BdkNetwork,
    mnemonic: BdkMnemonic,
  ): BdkDescriptorSecretKey {
    require(mnemonic is BdkMnemonicImpl)
    return BdkDescriptorSecretKeyImpl(
      ffiDescriptorSecretKey =
        FfiDescriptorSecretKey(
          network = network.ffiNetwork,
          mnemonic = mnemonic.ffiMnemonic,
          password = null
        )
    )
  }
}
