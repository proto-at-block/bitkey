import CryptoKit
import Foundation
import Shared

public class SymmetricKeyGeneratorImpl: SymmetricKeyGenerator {

    public init() {}

    public func generate() -> Shared.SymmetricKey {
        let key = CryptoKit.SymmetricKey(size: .bits256)
        return Shared.SymmetricKeyKt.SymmetricKey(data: key.toData())
    }

}
