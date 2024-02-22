import BitcoinDevKit
import Shared

class BdkDescriptorFactoryImpl: BdkDescriptorFactory {

    func bip84(
      secretsKey: BdkDescriptorSecretKey,
      keychain: BdkKeychainKind,
      network: BdkNetwork
    ) -> BdkDescriptor {
        let secretsKeyImpl = secretsKey as! BdkDescriptorSecretKeyImpl

        let descriptor = Descriptor
          .newBip84(
            secretKey: secretsKeyImpl.ffiDescriptorSecretKey,
            keychain: keychain.ffiKeychainKind,
            network: network.ffiNetwork
        )

        return BdkDescriptorImpl(ffiDescriptor: descriptor)
    }
}

