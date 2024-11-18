import core
import Foundation
import Shared

public class PublicKeyImpl: Shared.PublicKey {
    let corePublicKey: core.PublicKey

    public init(corePublicKey: core.PublicKey) {
        self.corePublicKey = corePublicKey
    }
}
