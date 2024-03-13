import Foundation
import core
import Shared

public final class Spake2Impl: Shared.Spake2 {
    public init () {}
    
    public func generateKeyPair(spake2Params: Spake2Params) throws -> Spake2KeyPair {
        let ctx = try core.Spake2Context(myRole: toCore(role: spake2Params.role), myName: spake2Params.myName, theirName: spake2Params.theirName)
        let publicKey = try ctx.generateMsg(password: spake2Params.password.toData())
        let privateKey =  ctx.readPrivateKey()
        let publicKeyByteString = OkioKt.ByteString(data: publicKey)
        let privateKeyByteString = OkioKt.ByteString(data: privateKey)
        
        return Spake2KeyPair(privateKey: Spake2PrivateKey(bytes: privateKeyByteString), publicKey: Spake2PublicKey(bytes: publicKeyByteString))
    }
    
    public func processTheirPublicKey(spake2Params: Spake2Params, myKeyPair: Spake2KeyPair, theirPublicKey: Spake2PublicKey, aad: OkioByteString?) throws -> Spake2SymmetricKeys {
        let ctx = try core.Spake2Context(myRole: toCore(role: spake2Params.role), myName: spake2Params.myName, theirName: spake2Params.theirName)
        // Write password to context
        let _ = try ctx.generateMsg(password: spake2Params.password.toData())
        // Write key pair to context
        try ctx.writeKeyPair(privateKey: myKeyPair.privateKey.bytes.toData(), publicKey: myKeyPair.publicKey.bytes.toData())
        let result = try ctx.processMsg(theirMsg: theirPublicKey.bytes.toData(), aad: aad?.toData())
        
        return Spake2SymmetricKeys(
            aliceEncryptionKey: OkioKt.ByteString(data: result.aliceEncryptionKey),
            bobEncryptionKey: OkioKt.ByteString(data: result.bobEncryptionKey),
            aliceConfKey: OkioKt.ByteString(data: result.aliceConfKey),
            bobConfKey: OkioKt.ByteString(data: result.bobConfKey)
        )
    }
    
    public func generateKeyConfMsg(role: Shared.Spake2Role, keys: Spake2SymmetricKeys) throws -> OkioByteString {
        // Name strings aren't used by `generateKeyConfMsg`, so we can set them to alice and bob rather
        // than requiring them as parameters.
        let ctx = try core.Spake2Context(myRole: toCore(role: role), myName: "alice", theirName: "bob")
        let result = try ctx.generateKeyConfMsg(keys: toCore(keys: keys))
        
        return OkioKt.ByteString(data: result)
    }
    
    public func processKeyConfMsg(role: Shared.Spake2Role, receivedKeyConfMsg: OkioByteString, keys: Spake2SymmetricKeys) throws {
        // Name strings aren't used by `generateKeyConfMsg`, so we can set them to alice and bob rather
        // than requiring them as parameters.
        let ctx = try core.Spake2Context(myRole: toCore(role: role), myName: "alice", theirName: "bob")
        
        try ctx.processKeyConfMsg(receivedMac: receivedKeyConfMsg.toData(), keys: toCore(keys: keys))
    }
    
    func toCore(role: Shared.Spake2Role) -> core.Spake2Role {
        switch role {
        case .alice:
            return core.Spake2Role.alice
        case .bob:
            return core.Spake2Role.bob
        default:
            fatalError("Unexpected Spake2Role case")
        }
    }
    
    func toCore(keys: Spake2SymmetricKeys) -> core.Spake2Keys {
        return core.Spake2Keys(
            aliceEncryptionKey: keys.aliceEncryptionKey.toData(),
            bobEncryptionKey: keys.bobEncryptionKey.toData(),
            aliceConfKey: keys.aliceConfKey.toData(),
            bobConfKey: keys.bobConfKey.toData()
        )
    }
}
