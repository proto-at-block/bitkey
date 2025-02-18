import core
import Foundation
import Shared

class NoiseInitiatorImpl: Shared.NoiseInitiator {
    private let secureEnclave: SecureEnclave
    private let symmetricKeyGenerator: SymmetricKeyGenerator
    private var sessionContexts = [NoiseKeyVariant: NoiseContextImpl]()
    private var hardwareBackedDh: core.HardwareBackedDh

    private func generatePrivateKey(keyType: NoiseKeyVariant) throws -> core.PrivateKey {
        let privateKeyName = "noise-initiator-sk-\(keyType.name)"

        do {
            try secureEnclave.generateP256KeyPair(
                spec: SeKeySpec(
                    name: privateKeyName,
                    purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
                    usageConstraints: SeKeyUsageConstraints.none,
                    validity: nil
                )
            )
            return core.PrivateKey.hardwareBacked(name: privateKeyName)
        } catch let error as Shared.SecureEnclaveError where error == .NoSecureEnclave.shared {
            return core.PrivateKey
                .inMemory(secretBytes: symmetricKeyGenerator.generate().raw.toData())
        } catch {
            throw error
        }
    }

    private func sessionContext(keyType: NoiseKeyVariant) throws -> NoiseContextImpl {
        if let existingContext = sessionContexts[keyType] {
            return existingContext
        }

        let privateKey = try generatePrivateKey(keyType: keyType)
        let noiseContext = try NoiseContextImpl(
            role: core.NoiseRole.initiator,
            privateKey: privateKey,
            theirPublicKey: Data(base64Encoded: keyType.serverStaticPubkey),
            dh: self.hardwareBackedDh
        )

        sessionContexts[keyType] = noiseContext
        return noiseContext
    }

    init(secureEnclave: SecureEnclave, symmetricKeyGenerator: SymmetricKeyGenerator) {
        self.secureEnclave = secureEnclave
        self.symmetricKeyGenerator = symmetricKeyGenerator
        self.hardwareBackedDh = CoreHardwareBackedDhImpl(
            secureEnclave: self.secureEnclave,
            name: "mobile-app"
        )
    }

    func initiateHandshake(keyType: NoiseKeyVariant) throws -> InitiateHandshakeMessage {
        sessionContexts
            .removeValue(forKey: keyType) // Remove existing session state if re-initiating
        // handshake

        let context = try sessionContext(keyType: keyType)
        let payload = try context.initiateHandshake()

        return InitiateHandshakeMessage(payload: payload.asKotlinByteArray)
    }

    func advanceHandshake(keyType: NoiseKeyVariant, peerHandshakeMessage: KotlinByteArray) throws {
        _ = try sessionContext(keyType: keyType).advanceHandshake(
            peerHandshakeMessage: peerHandshakeMessage.asData()
        )
    }

    func finalizeHandshake(keyType: NoiseKeyVariant) throws {
        try sessionContext(keyType: keyType).finalizeHandshake()
    }

    func encryptMessage(
        keyType: NoiseKeyVariant,
        message: KotlinByteArray
    ) throws -> KotlinByteArray {
        return try sessionContext(keyType: keyType).encryptMessage(
            message: message.asData()
        ).asKotlinByteArray
    }

    func decryptMessage(
        keyType: NoiseKeyVariant,
        ciphertext: KotlinByteArray
    ) throws -> KotlinByteArray {
        return try sessionContext(keyType: keyType).decryptMessage(
            ciphertext: ciphertext.asData()
        ).asKotlinByteArray
    }
}
