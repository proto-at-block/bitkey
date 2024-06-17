import CryptoKit
import Foundation

/// Provides tools for generating keys to be used for encrypting data.
public protocol EncryptionKeyGenerator: AnyObject {

    /// Generates and returns a 256-bit encryption key.
    /// - Returns: The encryption key.
    func generateEncryptionKey() -> [UInt8]

}

public extension SymmetricKey {
    func toBytes() -> [UInt8] { self.withUnsafeBytes { Array($0) }}
    func toData() -> Data { self.withUnsafeBytes { Data($0) }}
}
