import XCTest
import Shared
import Foundation

@testable import Wallet

class SignatureVerifierImplTests: XCTestCase {
    let signatureVerifier = SignatureVerifierImpl()
    let keyGenerator = Secp256k1KeyGeneratorImpl()
    let messageSigner = MessageSignerImpl()

    func test_verifyEcdsa() throws {
        let keyPair = try keyGenerator.generateKeypair()
        let message = OkioByteString.companion.encodeUtf8("Hello world!")
        let signature = try messageSigner.sign(message: message, key: keyPair.privateKey)
        XCTAssertTrue(try signatureVerifier.verifyEcdsa(message: message, signature: signature, publicKey: keyPair.publicKey).isValid)
        let invalidMessage = OkioByteString.companion.encodeUtf8("Helloworld!")
        XCTAssertThrowsError(try signatureVerifier.verifyEcdsa(message: invalidMessage, signature: signature, publicKey: keyPair.publicKey))
        let invalidSignature = "malformed signature"
        XCTAssertThrowsError(try signatureVerifier.verifyEcdsa(message: message, signature: invalidSignature, publicKey: keyPair.publicKey))
    }
}
