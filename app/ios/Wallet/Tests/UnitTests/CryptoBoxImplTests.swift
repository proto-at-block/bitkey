import XCTest
import Shared
import Foundation

@testable import Wallet

class CryptoBoxImplTests: XCTestCase {
    let cryptoBox = CryptoBoxImpl()
    let nonceGenerator = XNonceGeneratorImpl()
    
    func test_encryptAndDecrypt() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()
        
        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try cryptoBox.encrypt(theirPublicKey: bobKeyPair.publicKey, myPrivateKey: aliceKeyPair.privateKey, nonce: nonce, plaintext: plaintext)
        
        // Bob decrypts
        let decrypted = try cryptoBox.decrypt(theirPublicKey: aliceKeyPair.publicKey, myPrivateKey: bobKeyPair.privateKey, sealedData: sealedData)
        
        XCTAssertEqual(decrypted, plaintext)
    }
    
    func test_emptyPlaintext() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()
        
        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("")
        let sealedData = try cryptoBox.encrypt(theirPublicKey: bobKeyPair.publicKey, myPrivateKey: aliceKeyPair.privateKey, nonce: nonce, plaintext: plaintext)
        
        // Bob decrypts
        let decrypted = try cryptoBox.decrypt(theirPublicKey: aliceKeyPair.publicKey, myPrivateKey: bobKeyPair.privateKey, sealedData: sealedData)
        
        XCTAssertEqual(decrypted, plaintext)
    }
    
    func test_bitFlip() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()
        
        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let ciphertextWithMetadata = try cryptoBox.encrypt(theirPublicKey: bobKeyPair.publicKey, myPrivateKey: aliceKeyPair.privateKey, nonce: nonce, plaintext: plaintext)
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
        XCTAssertThrowsError(try cryptoBox.decrypt(theirPublicKey: aliceKeyPair.publicKey, myPrivateKey: bobKeyPair.privateKey, sealedData: modifiedSealedData.toOpaqueCiphertext()))
    }
    
    func test_wrongKey() throws {
        let aliceKeyPair = cryptoBox.generateKeyPair()
        let bobKeyPair = cryptoBox.generateKeyPair()
        
        // Alice encrypts
        let nonce = try nonceGenerator.generateXNonce()
        let plaintext = OkioByteString.companion.encodeUtf8("Hello, world!")
        let sealedData = try cryptoBox.encrypt(theirPublicKey: bobKeyPair.publicKey, myPrivateKey: aliceKeyPair.privateKey, nonce: nonce, plaintext: plaintext)
        
        // Bob attempts to decrypt
        let charlieKeyPair = cryptoBox.generateKeyPair()
        XCTAssertThrowsError(try cryptoBox.decrypt(theirPublicKey: aliceKeyPair.publicKey, myPrivateKey: charlieKeyPair.privateKey, sealedData: sealedData))
    }
}
