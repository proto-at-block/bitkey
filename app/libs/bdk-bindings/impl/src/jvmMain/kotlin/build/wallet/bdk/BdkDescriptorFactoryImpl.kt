package build.wallet.bdk

import build.wallet.bdk.bindings.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import org.bitcoindevkit.Descriptor

@BitkeyInject(AppScope::class)
class BdkDescriptorFactoryImpl : BdkDescriptorFactory {
  override fun bip84(
    secretsKey: BdkDescriptorSecretKey,
    keychain: BdkKeychainKind,
    network: BdkNetwork,
  ): BdkDescriptor {
    return BdkDescriptorImpl(
      Descriptor.newBip84(
        secretKey = (secretsKey as BdkDescriptorSecretKeyImpl).ffiDescriptorSecretKey,
        keychain = keychain.ffiKeychainKind,
        network = network.ffiNetwork
      )
    )
  }
}
