import CryptoKit
import Foundation

// MARK: -

public final class EncryptionManagerImpl: EncryptionManager {

    // MARK: - Life Cycle

    public init() {}

    // MARK: - EncryptionManager

    public func seal(
        _ data: Data,
        with dataKey: Data
    ) throws -> Data {
        let symmetricDataKey = SymmetricKey(data: dataKey)
        let encryptedContent = try ChaChaPoly.seal(data, using: symmetricDataKey)
        return encryptedContent.combined
    }

    public func unseal(
        _ sealedData: Data,
        with dataKey: Data
    ) throws -> Data {
        let sealedBox = try ChaChaPoly.SealedBox(combined: sealedData)
        let symmetricDataKey = SymmetricKey(data: dataKey)
        let unsealedData = try ChaChaPoly.open(sealedBox, using: symmetricDataKey)

        return unsealedData
    }
}
