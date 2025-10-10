import core
import Foundation
import Shared

class PublicKeyUtilsImpl: Shared.PublicKeyUtils {
    func extractPublicKey(
        descriptorPublicKey: Shared
            .DescriptorPublicKey
    ) -> ChaincodeDelegationResult<NSString> {
        return ChaincodeDelegationResult {
            try core.extractPublicKey(descriptorPublicKey: descriptorPublicKey.dpub) as NSString
        }
    }
}
