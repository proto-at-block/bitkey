package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkDescriptor
import build.wallet.bdk.bindings.BdkDescriptorFactory
import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkKeychainKind
import build.wallet.bdk.bindings.BdkNetwork
import org.bitcoindevkit.Descriptor

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
