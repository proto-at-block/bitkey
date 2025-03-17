import Foundation
import Shared
import Testing

@testable import Wallet

struct CryptoBoxImplTests {
    let cryptoBox = CryptoBoxImpl()
    let nonceGenerator = XNonceGeneratorImpl()

    @Test
    func encryptAndDecrypt() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try cryptoBox.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext: plaintext
        )

        // Bob decrypts
        let decrypted = try cryptoBox.decrypt(
            theirPublicKey: aliceKeyPair.publicKey,
            myPrivateKey: bobKeyPair.privateKey,
            sealedData: sealedData
        )

        #expect(decrypted == plaintext)
    }

    @Test
    func emptyPlaintext() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("")
        let sealedData = try cryptoBox.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext: plaintext
        )

        // Bob decrypts
        let decrypted = try cryptoBox.decrypt(
            theirPublicKey: aliceKeyPair.publicKey,
            myPrivateKey: bobKeyPair.privateKey,
            sealedData: sealedData
        )

        #expect(decrypted == plaintext)
    }

    @Test
    func bitFlip() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let ciphertextWithMetadata = try cryptoBox.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext: plaintext
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
            try cryptoBox.decrypt(
                theirPublicKey: aliceKeyPair.publicKey,
                myPrivateKey: bobKeyPair.privateKey,
                sealedData: modifiedSealedData.toOpaqueCiphertext()
            )
        }
    }

    @Test
    func wrongKey() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()

        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try cryptoBox.encrypt(
            theirPublicKey: bobKeyPair.publicKey,
            myPrivateKey: aliceKeyPair.privateKey,
            nonce: nonce,
            plaintext: plaintext
        )

        // Bob attempts to decrypt
        let charlieKeyPair = cryptoBox.generateKeyPair()
        #expect(throws: (any Error).self) {
            try cryptoBox.decrypt(
                theirPublicKey: aliceKeyPair.publicKey,
                myPrivateKey: charlieKeyPair.privateKey,
                sealedData: sealedData
            )
        }
    }
}
