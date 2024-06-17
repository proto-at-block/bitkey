import BitcoinDevKit
import Shared

class BdkDescriptorSecretKeyFactoryImpl: BdkDescriptorSecretKeyFactory {

    func fromString(secretKey: String) -> BdkDescriptorSecretKey {
        return BdkDescriptorSecretKeyImpl(
            ffiDescriptorSecretKey: try! DescriptorSecretKey
                .fromString(secretKey: secretKey)
        )
    }
}
