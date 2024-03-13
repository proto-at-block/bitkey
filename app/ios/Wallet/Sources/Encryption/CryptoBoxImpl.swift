import Foundation
import core
import Shared

public final class CryptoBoxImpl: Shared.CryptoBox {
    public init () {}
    
    public func generateKeyPair() -> Shared.CryptoBoxKeyPair {
        let keyPair = core.CryptoBoxKeyPair()
        let publicKey = CryptoBoxPublicKey(bytes: OkioKt.ByteString(data: keyPair.publicKey()))
        let privateKey = CryptoBoxPrivateKey(bytes: OkioKt.ByteString(data: keyPair.secretKey()))
        
        return CryptoBoxKeyPair(privateKey: privateKey, publicKey: publicKey)
    }
    
    public func decrypt(theirPublicKey: CryptoBoxPublicKey, myPrivateKey: CryptoBoxPrivateKey, sealedData: XCiphertext) throws -> OkioByteString {
        let cryptoBox = try core.CryptoBox(publicKey: theirPublicKey.bytes.toData(), secretKey: myPrivateKey.bytes.toData())
        let xSealedData = try sealedData.toXSealedData()
        let plaintext = try cryptoBox.decrypt(nonce: xSealedData.nonce.bytes.toData(), ciphertext: xSealedData.ciphertext.toData())
        
        return OkioKt.ByteString(data: plaintext)
    }
    
    public func encrypt(theirPublicKey: CryptoBoxPublicKey, myPrivateKey: CryptoBoxPrivateKey, nonce: XNonce, plaintext: OkioByteString) throws -> XCiphertext {
        let cryptoBox = try core.CryptoBox(publicKey: theirPublicKey.bytes.toData(), secretKey: myPrivateKey.bytes.toData())
        let ciphertext = try cryptoBox.encrypt(nonce: nonce.bytes.toData(), plaintext: plaintext.toData())
        
        return XSealedData(header: XSealedData.Header(version: 1, algorithm: CryptoBoxCompanion().ALGORITHM), ciphertext: OkioKt.ByteString(data: ciphertext), nonce: nonce, publicKey: nil).toOpaqueCiphertext()
    }
}

