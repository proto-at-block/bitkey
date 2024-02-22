import Foundation
import CryptoKit
import Shared

public class SymmetricKeyEncryptorImpl : SymmetricKeyEncryptor {

    public init() {}

    public func seal(unsealedData: OkioByteString, key: Shared.SymmetricKey) -> SealedData {
        let symmetricKey = SymmetricKey(data: key.raw.toData())
        
        let encryptedContent = try! ChaChaPoly.seal(unsealedData.toData(), using: symmetricKey)
        
        let nonce = OkioKt.ByteString(data: Data(encryptedContent.nonce))
        let ciphertext = OkioKt.ByteString(data: encryptedContent.ciphertext)
        let tag = OkioKt.ByteString(data: encryptedContent.tag)
        
        return SealedData(ciphertext: ciphertext, nonce: nonce, tag: tag)
    }
    
    public func unseal(sealedData: Shared.SealedData, key: Shared.SymmetricKey) -> OkioByteString {
        let nonce = try! ChaChaPoly.Nonce(data: sealedData.nonce.toData())
        let ciphertext = sealedData.ciphertext.toData()
        let tag = sealedData.tag.toData()
        
        let sealedBox = try! ChaChaPoly.SealedBox(
            nonce: nonce,
            ciphertext: ciphertext,
            tag: tag
        )
        
        let symmetricKey = SymmetricKey(data: key.raw.toData())
        let unsealedData = try! ChaChaPoly.open(sealedBox, using: symmetricKey)
        
        return OkioKt.ByteString(data: unsealedData)
    }

}
