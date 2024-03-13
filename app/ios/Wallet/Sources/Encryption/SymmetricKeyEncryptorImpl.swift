import Foundation
import CryptoKit
import Shared
import core

public class SymmetricKeyEncryptorImpl : SymmetricKeyEncryptor {
    let cipher = XChaCha20Poly1305Impl()

    public init() {}

    public func seal(unsealedData: OkioByteString, key: Shared.SymmetricKey) throws -> SealedData {
        let nonce = try XNonceGeneratorImpl().generateXNonce()
        return try cipher.encryptNoMetadata(
            key: key,
            plaintext: unsealedData,
            nonce: nonce.bytes,
            aad: OkioKt.ByteString(data: Data())
        )
    }

    public func unseal(sealedData: Shared.SealedData, key: Shared.SymmetricKey) throws -> OkioByteString {
        return try cipher.decryptNoMetadata(key: key, sealedData: sealedData, aad: OkioKt.ByteString(data: Data()))
    }

}
