import Foundation
import core
import Shared

enum XChaCha20Poly1305Error: Error {
    case notSupported
}

public final class XChaCha20Poly1305Impl: Shared.XChaCha20Poly1305 {
    public init () {}

    public func encrypt(key: Shared.SymmetricKey, nonce: Shared.XNonce, plaintext: OkioByteString, aad: OkioByteString = OkioByteString.Companion.shared.EMPTY) throws -> Shared.XCiphertext {
        let cipher = core.XChaCha20Poly1305(key: key.raw.toData())
        let ciphertext = try cipher.encrypt(nonce: nonce.bytes.toData(), plaintext: plaintext.toData(), aad: aad.toData())
        return Shared.XSealedData(
            header: Shared.XSealedData.Header(version: 1, algorithm: Shared.XChaCha20Poly1305Companion().ALGORITHM),
            ciphertext: OkioKt.ByteString(data: ciphertext),
            nonce: nonce,
            publicKey: nil
        ).toOpaqueCiphertext()
    }

    public func decrypt(key: Shared.SymmetricKey, ciphertextWithMetadata: XCiphertext, aad: OkioByteString = OkioByteString.Companion.shared.EMPTY) throws -> OkioByteString {
        let cipher = core.XChaCha20Poly1305(key: key.raw.toData())
        let sealedData = try ciphertextWithMetadata.toXSealedData()
        let plaintext = try cipher.decrypt(nonce: sealedData.nonce.bytes.toData(), ciphertext: sealedData.ciphertext.toData(), aad: aad.toData())
        return OkioKt.ByteString(data: plaintext)
    }

    public func decryptNoMetadata(key: SymmetricKey, sealedData: SealedData, aad: OkioByteString) throws -> OkioByteString {
        // iOS uses CryptoKit
        throw XChaCha20Poly1305Error.notSupported
    }

    public func encryptNoMetadata(key: SymmetricKey, plaintext: OkioByteString, nonce: OkioByteString, aad: OkioByteString) throws -> SealedData {
        // iOS uses CryptoKit
        throw XChaCha20Poly1305Error.notSupported
    }
}
