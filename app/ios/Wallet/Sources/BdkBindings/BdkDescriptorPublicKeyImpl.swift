import BitcoinDevKitLegacy
import Shared

class BdkDescriptorPublicKeyImpl: BdkDescriptorPublicKey {
    private let ffiBdkDescriptorPublicKey: BitcoinDevKitLegacy.DescriptorPublicKey

    init(ffiBdkDescriptorPublicKey: BitcoinDevKitLegacy.DescriptorPublicKey) {
        self.ffiBdkDescriptorPublicKey = ffiBdkDescriptorPublicKey
    }

    func raw() -> String {
        return ffiBdkDescriptorPublicKey.asString()
    }
}
