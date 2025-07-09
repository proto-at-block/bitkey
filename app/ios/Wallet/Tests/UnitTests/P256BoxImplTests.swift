import Foundation
import Shared
import Testing

@testable import Wallet

struct P256BoxImplTests {
    let p256Box = P256BoxImpl()
    let nonceGenerator = XNonceGeneratorImpl()

    @Test
    func encryptAndDecrypt() throws {
        let aliceKeyPair = p256Box.generateKeyPair()
        let bobKeyPair = p256Box.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try p256Box.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext_: plaintext
        )

        // Bob decrypts
        let decrypted = try p256Box.decrypt(
            theirPublicKey: aliceKeyPair.publicKey,
            myPrivateKey: bobKeyPair.privateKey,
            sealedData_: sealedData
        )

        #expect(decrypted == plaintext)
    }

    @Test
    func emptyPlaintext() throws {
        let aliceKeyPair = p256Box.generateKeyPair()
        let bobKeyPair = p256Box.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("")
        let sealedData = try p256Box.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext_: plaintext
        )

        // Bob decrypts
        let decrypted = try p256Box.decrypt(
            theirPublicKey: aliceKeyPair.publicKey,
            myPrivateKey: bobKeyPair.privateKey,
            sealedData_: sealedData
        )

        #expect(decrypted == plaintext)
    }

    @Test
    func bitFlip() throws {
        let aliceKeyPair = p256Box.generateKeyPair()
        let bobKeyPair = p256Box.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let ciphertextWithMetadata = try p256Box.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext_: plaintext
        )
        let sealedData = try ciphertextWithMetadata.toXSealedData()

        // Flip a bit in the sealed data
        var modifiedCiphertext = sealedData.ciphertext.toByteArray().asUInt8Array()
        modifiedCiphertext[0] ^= 1
        let modifiedSealedData = Shared.XSealedData(
            header: sealedData.header,
            ciphertext: OkioKt.ByteString(data: Data(modifiedCiphertext)),
            nonce: sealedData.nonce,
            publicKey: nil
        )

        // Bob attempts to decrypt
        #expect(throws: (any Error).self) {
            try p256Box.decrypt(
                theirPublicKey: aliceKeyPair.publicKey,
                myPrivateKey: bobKeyPair.privateKey,
                sealedData_: modifiedSealedData.toOpaqueCiphertext()
            )
        }
    }

    @Test
    func wrongKey() throws {
        let aliceKeyPair = p256Box.generateKeyPair()
        let bobKeyPair = p256Box.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try p256Box.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext_: plaintext
        )

        // Bob attempts to decrypt
        let charlieKeyPair = p256Box.generateKeyPair()
        #expect(throws: (any Error).self) {
            try p256Box.decrypt(
                theirPublicKey: aliceKeyPair.publicKey,
                myPrivateKey: charlieKeyPair.privateKey,
                sealedData_: sealedData
            )
        }
    }
}
