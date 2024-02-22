import XCTest
import Shared
import Foundation
import core

@testable import Wallet

class XChaCha20Poly1305ImplTests: XCTestCase {

    func test_encryption_without_aad() throws {
        let keyGenerator = SymmetricKeyGeneratorImpl()
        let key = keyGenerator.generate()
        let nonceGenerator = XNonceGeneratorImpl()
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioKt.ByteString(data: "Hello world!".data(using: String.Encoding.utf8)!)
        let cipher = XChaCha20Poly1305Impl()

        let ciphertextWithMetadata = try cipher.encrypt(key: key, nonce: nonce, plaintext: plaintext)
        let decrypted = try cipher.decrypt(key: key, ciphertextWithMetadata: ciphertextWithMetadata)

        XCTAssertEqual(plaintext, decrypted)
    }

    func test_encryption_with_aad() throws {
        let keyGenerator = SymmetricKeyGeneratorImpl()
        let key = keyGenerator.generate()
        let nonceGenerator = XNonceGeneratorImpl()
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioKt.ByteString(data: "Hello world!".data(using: String.Encoding.utf8)!)
        let aad = OkioKt.ByteString(data: "Lorem Ipsum".data(using: String.Encoding.utf8)!)
        let wrongAad = OkioKt.ByteString(data: "Wrong aad".data(using: String.Encoding.utf8)!)
        let cipher = XChaCha20Poly1305Impl()

        let ciphertextWithMetadata = try cipher.encrypt(key: key, nonce: nonce, plaintext: plaintext, aad: aad)
        let decrypted = try cipher.decrypt(key: key, ciphertextWithMetadata: ciphertextWithMetadata, aad: aad)

        XCTAssertEqual(plaintext, decrypted)
        XCTAssertThrowsError(try cipher.decrypt(key: key, ciphertextWithMetadata: ciphertextWithMetadata, aad: wrongAad)) { error in
            XCTAssertEqual(error as! core.ChaCha20Poly1305Error, core.ChaCha20Poly1305Error.DecryptError(message: "Failed to decrypt"))
        }
        XCTAssertThrowsError(try cipher.decrypt(key: key, ciphertextWithMetadata: ciphertextWithMetadata)) { error in
            XCTAssertEqual(error as! core.ChaCha20Poly1305Error, core.ChaCha20Poly1305Error.DecryptError(message: "Failed to decrypt"))
        }
    }

    func test_authentication() throws {
        let keyGenerator = SymmetricKeyGeneratorImpl()
        let key = keyGenerator.generate()
        let nonceGenerator = XNonceGeneratorImpl()
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioKt.ByteString(data: "Hello world!".data(using: String.Encoding.utf8)!)
        let cipher = XChaCha20Poly1305Impl()

        let ciphertextWithMetadata = try cipher.encrypt(key: key, nonce: nonce, plaintext: plaintext)
        let sealedData = try ciphertextWithMetadata.toXSealedData()
        var modifiedCiphertext = sealedData.ciphertext.toByteArray().asUInt8Array()
        modifiedCiphertext[0] ^= 0x01
        let modifiedSealedData = Shared.XSealedData(
            header: sealedData.header,
            ciphertext: OkioKt.ByteString(data: Data(modifiedCiphertext)),
            nonce: sealedData.nonce,
            publicKey: nil
        )

        XCTAssertThrowsError(try cipher.decrypt(key: key, ciphertextWithMetadata: modifiedSealedData.toOpaqueCiphertext())) { error in
            XCTAssertEqual(error as! core.ChaCha20Poly1305Error, core.ChaCha20Poly1305Error.DecryptError(message: "Failed to decrypt"))
        }
    }

    // https://datatracker.ietf.org/doc/html/draft-arciszewski-xchacha-03#appendix-A.3.1
    func test_rfc_vector() throws {
        let plaintext = OkioByteString.Companion.shared.decodeHex(
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
            "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
            "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
            "637265656e20776f756c642062652069742e"
        )
        let aad = OkioByteString.Companion.shared.decodeHex("50515253c0c1c2c3c4c5c6c7")
        let key = OkioByteString.Companion.shared.decodeHex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        let iv = OkioByteString.Companion.shared.decodeHex("404142434445464748494a4b4c4d4e4f5051525354555657")
        let expectedCiphertext = OkioByteString.Companion.shared.decodeHex(
            "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
            "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
            "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
            "21f9664c97637da9768812f615c68b13b52e"
        )
        let expectedTag = OkioByteString.Companion.shared.decodeHex("c0875924c1c7987947deafd8780acf49")
        let cipher = XChaCha20Poly1305Impl()

        let out = try cipher.encrypt(
            key: Shared.SymmetricKeyKt.SymmetricKey(data: key.toData()),
            nonce: Shared.XNonce(bytes: iv),
            plaintext: plaintext,
            aad: aad
        )
        let sealedData = try out.toXSealedData()
        let tagSize = expectedTag.toByteArray().asUInt8Array().count
        let outSize = sealedData.ciphertext.toByteArray().asUInt8Array().count
        let tagIndex = outSize - tagSize
        let ciphertext = sealedData.ciphertext.toData().subdata(in: 0..<tagIndex)
        let tag = sealedData.ciphertext.toData().subdata(in: tagIndex..<outSize)

        XCTAssertEqual(tag, expectedTag.toData())
        XCTAssertEqual(ciphertext, expectedCiphertext.toData())
    }
}
