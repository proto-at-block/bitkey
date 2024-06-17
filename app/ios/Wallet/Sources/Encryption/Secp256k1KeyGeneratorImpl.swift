import core
import Foundation
import Shared

public final class Secp256k1KeyGeneratorImpl: Secp256k1KeyGenerator {
    public init() {}

    public func derivePublicKey(privateKey: Shared.Secp256k1PrivateKey) -> Shared
        .Secp256k1PublicKey
    {
        let coreSecretKey = try! core.SecretKey(secretBytes: [UInt8](privateKey.bytes.toData()))

        return Shared.Secp256k1PublicKey(value: coreSecretKey.asPublic())
    }

    public func generatePrivateKey() throws -> Shared.Secp256k1PrivateKey {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(
            kSecRandomDefault,
            32,
            &bytes
        )
        guard status == errSecSuccess else {
            throw Shared.Secp256k1KeyGeneratorError
                .PrivateKeyGenerationError(
                    message: "SecRandomCopyBytes failed with status: \(status)"
                )
                .asError()
        }
        // Check whether the private key is valid by passing it to the Core constructor
        try core.SecretKey(secretBytes: bytes)
        return Shared.Secp256k1PrivateKey(bytes: OkioKt.ByteString(data: Data(bytes)))
    }

    public func generateKeypair() throws -> Shared.Secp256k1Keypair {
        let privateKey = try generatePrivateKey()
        let publicKey = derivePublicKey(privateKey: privateKey)
        return Shared.Secp256k1Keypair(publicKey: publicKey, privateKey: privateKey)
    }
}
