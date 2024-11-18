import core
import Shared

class NoiseContextImpl: core.NoiseContextProtocol {
    var coreContext: core.NoiseContext

    public init(
        role: core.NoiseRole,
        privkeyName: String,
        theirPublicKey: Data?,
        dh: core.HardwareBackedDh
    ) throws {
        coreContext = try core.NoiseContext(
            role: role,
            privkey: core.PrivateKey.hardwareBacked(name: privkeyName),
            theirPublicKey: theirPublicKey,
            dh: dh
        )
    }

    func advanceHandshake(peerHandshakeMessage: Data) throws -> Data? {
        try coreContext.advanceHandshake(peerHandshakeMessage: peerHandshakeMessage)
    }

    func decryptMessage(ciphertext: Data) throws -> Data {
        try coreContext.decryptMessage(ciphertext: ciphertext)
    }

    func encryptMessage(message: Data) throws -> Data {
        try coreContext.encryptMessage(message: message)
    }

    func finalizeHandshake() throws {
        try coreContext.finalizeHandshake()
    }

    func initiateHandshake() throws -> Data {
        try coreContext.initiateHandshake()
    }
}
