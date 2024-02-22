import Foundation
import core
import Shared

public final class Secp256k1SharedSecretImpl: Shared.Secp256k1SharedSecret {
    public init () {}
    
    public func deriveSharedSecret(privateKey: Shared.Secp256k1PrivateKey, publicKey: Shared.Secp256k1PublicKey) -> OkioByteString {
        let coreSecretKey = try! core.SecretKey(secretBytes: [UInt8](privateKey.bytes.toData()))
        let sharedSecret = core.Secp256k1SharedSecret(point: publicKey.value, scalar: coreSecretKey)
        
        return OkioKt.ByteString(data: Data(sharedSecret.secretBytes()))
    }
}
