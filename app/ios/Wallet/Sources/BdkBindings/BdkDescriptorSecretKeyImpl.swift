import BitcoinDevKit
import Shared

class BdkDescriptorSecretKeyImpl: BdkDescriptorSecretKey {

    let ffiDescriptorSecretKey: DescriptorSecretKey

    init(ffiDescriptorSecretKey: DescriptorSecretKey) {
        self.ffiDescriptorSecretKey = ffiDescriptorSecretKey
    }

    func asPublic() -> BdkDescriptorPublicKey {
        return BdkDescriptorPublicKeyImpl(
            ffiBdkDescriptorPublicKey: ffiDescriptorSecretKey
                .asPublic()
        )
    }

    func derive(path: BdkDerivationPath) -> BdkResult<BdkDescriptorSecretKey> {
        return BdkResult {
            try BdkDescriptorSecretKeyImpl(
                ffiDescriptorSecretKey: ffiDescriptorSecretKey.derive(path: .init(path: path.path))
            )
        }
    }

    func extend(path: BdkDerivationPath) -> BdkResult<BdkDescriptorSecretKey> {
        return BdkResult {
            try BdkDescriptorSecretKeyImpl(
                ffiDescriptorSecretKey: ffiDescriptorSecretKey.extend(path: .init(path: path.path))
            )
        }
    }

    func raw() -> String {
        return ffiDescriptorSecretKey.asString()
    }

    func secretBytes() -> KotlinByteArray {
        return KotlinByteArray(ffiDescriptorSecretKey.secretBytes())
    }

}
