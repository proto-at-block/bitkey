import CryptoKit
import Foundation

public class EncryptionKeyGeneratorImpl: EncryptionKeyGenerator {
    
    public init(){}
    
    public func generateEncryptionKey() -> [UInt8] {
        return SymmetricKey(size: .bits256).toBytes()
    }
    
}
