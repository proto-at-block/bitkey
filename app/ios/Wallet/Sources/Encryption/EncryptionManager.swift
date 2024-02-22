import Foundation

public protocol EncryptionManager: AnyObject {

    /// Seals the given data with the given data key.
    func seal(
        _ data: Data,
        with dataKey: Data
    ) throws -> Data

    /// Unseals the given sealed data with the given data key and returns the unsealed data.
    func unseal(
        _ sealedData: Data,
        with dataKey: Data
    ) throws -> Data

}
