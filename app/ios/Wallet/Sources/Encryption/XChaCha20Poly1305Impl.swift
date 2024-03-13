import Foundation
import core
import Shared

enum XChaCha20Poly1305Error: Error {
    case notSupported
}

public final class XChaCha20Poly1305Impl: Shared.XChaCha20Poly1305 {
    private let tagLength = 16

    public init () {}

    public func encrypt(key: Shared.SymmetricKey, nonce: Shared.XNonce, plaintext: OkioByteString, aad: OkioByteString = OkioByteString.Companion.shared.EMPTY) throws -> Shared.XCiphertext {
        let cipher = try core.XChaCha20Poly1305(key: key.raw.toData())
        let ciphertext = try cipher.encrypt(nonce: nonce.bytes.toData(), plaintext: plaintext.toData(), aad: aad.toData())
        return Shared.XSealedData(
            header: Shared.XSealedData.Header(version: 1, algorithm: Shared.XChaCha20Poly1305Companion().ALGORITHM),
            ciphertext: OkioKt.ByteString(data: ciphertext),
            nonce: nonce,
            publicKey: nil
        ).toOpaqueCiphertext()
    }

    public func decrypt(key: Shared.SymmetricKey, ciphertextWithMetadata: XCiphertext, aad: OkioByteString = OkioByteString.Companion.shared.EMPTY) throws -> OkioByteString {
        let cipher = try core.XChaCha20Poly1305(key: key.raw.toData())
        let sealedData = try ciphertextWithMetadata.toXSealedData()
        let plaintext = try cipher.decrypt(nonce: sealedData.nonce.bytes.toData(), ciphertext: sealedData.ciphertext.toData(), aad: aad.toData())
        return OkioKt.ByteString(data: plaintext)
    }

    public func decryptNoMetadata(key: SymmetricKey, sealedData: SealedData, aad: OkioByteString) throws -> OkioByteString {
        let cipher = try core.XChaCha20Poly1305(key: key.raw.toData())
        let ciphertextAndTag = sealedData.ciphertext.toData() + sealedData.tag.toData()
        let plaintext = try cipher.decrypt(nonce: sealedData.nonce.toData(), ciphertext: ciphertextAndTag, aad: aad.toData())
        return OkioKt.ByteString(data: plaintext)
    }

    public func encryptNoMetadata(key: SymmetricKey, plaintext: OkioByteString, nonce: OkioByteString, aad: OkioByteString) throws -> SealedData {
        let cipher = try core.XChaCha20Poly1305(key: key.raw.toData())
        let ciphertextAndTag = try cipher.encrypt(
            nonce: nonce.toData(),
            plaintext: plaintext.toData(),
            aad: aad.toData())

        let ciphertextLength = ciphertextAndTag.count - tagLength
        let ciphertext = ciphertextAndTag.subdata(in: 0..<ciphertextLength)
        let tag = ciphertextAndTag.subdata(in: ciphertextLength..<ciphertextAndTag.count)

        return SealedData(
            ciphertext: OkioKt.ByteString(data: ciphertext),
            nonce: nonce,
            tag: OkioKt.ByteString(data: tag)
        )
    }
}
