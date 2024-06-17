import core
import Foundation
import Shared

public final class MessageSignerImpl: MessageSigner {
    public init() {}

    public func sign(message: OkioByteString, key: Shared.Secp256k1PrivateKey) throws -> String {
        let coreSecKey = try core.SecretKey(secretBytes: [UInt8](key.bytes.toData()))

        return coreSecKey.signMessage(message: message.toByteArray().asUInt8Array())
    }
}
