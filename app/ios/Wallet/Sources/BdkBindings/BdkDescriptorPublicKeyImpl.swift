import BitcoinDevKit
import Shared

class BdkDescriptorPublicKeyImpl: BdkDescriptorPublicKey {
    private let ffiBdkDescriptorPublicKey: BitcoinDevKit.DescriptorPublicKey

    init(ffiBdkDescriptorPublicKey: BitcoinDevKit.DescriptorPublicKey) {
        self.ffiBdkDescriptorPublicKey = ffiBdkDescriptorPublicKey
    }

    func raw() -> String {
        return ffiBdkDescriptorPublicKey.asString()
    }
}
