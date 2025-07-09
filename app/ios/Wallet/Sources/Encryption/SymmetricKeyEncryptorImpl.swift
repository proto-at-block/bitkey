import core
import CryptoKit
import Foundation
import Shared

public class SymmetricKeyEncryptorImpl: SymmetricKeyEncryptor {
    let cipher = XChaCha20Poly1305Impl()

    public init() {}

    public func sealNoMetadata(
        unsealedData: OkioByteString,
        key: Shared.SymmetricKey
    ) throws -> SealedData {
        let nonce = try XNonceGeneratorImpl().generateXNonce()
        return try cipher.encryptNoMetadata(
            key: key,
            plaintext: unsealedData,
            nonce: nonce.bytes,
            aad: OkioKt.ByteString(data: Data())
        )
    }

    public func unsealNoMetadata(
        sealedData: Shared.SealedData,
        key: Shared.SymmetricKey
    ) throws -> OkioByteString {
        return try cipher.decryptNoMetadata(
            key: key,
            sealedData: sealedData,
            aad: OkioKt.ByteString(data: Data())
        )
    }

    public func seal(
        unsealedData: OkioByteString,
        key: Shared.SymmetricKey,
        aad: OkioByteString
    ) throws -> XCiphertext {
        let nonce = try XNonceGeneratorImpl().generateXNonce()
        return try cipher.encrypt(
            key: key,
            nonce: nonce,
            plaintext: unsealedData,
            aad: aad
        )
    }

    public func unseal(
        ciphertext: XCiphertext,
        key: Shared.SymmetricKey,
        aad: OkioByteString
    ) throws -> OkioByteString {
        return try cipher.decrypt(
            key: key,
            ciphertextWithMetadata: ciphertext,
            aad: aad
        )
    }
}
