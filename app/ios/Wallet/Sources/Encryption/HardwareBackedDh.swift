import core
import Shared

class CoreHardwareBackedDhImpl: core.HardwareBackedDh {
    private let secureEnclave: Shared.SecureEnclave
    private let name: String

    init(secureEnclave: Shared.SecureEnclave, name: String) {
        self.secureEnclave = secureEnclave
        self.name = name
    }

    func dh(ourPrivkeyName: String, peerPubkey: Data) throws -> Data {
        let ourPrivateKeyHandle = SeKeyHandle(name: ourPrivkeyName)
        let peerPublicKeyBytes = peerPubkey.asKotlinByteArray
        let peerPublicKey = SePublicKey(bytes: peerPublicKeyBytes)
        let sharedSecretBytes = try secureEnclave.diffieHellman(
            ourPrivateKey: ourPrivateKeyHandle,
            peerPublicKey: peerPublicKey
        )
        return sharedSecretBytes.asData()
    }

    func generate() throws -> core.HardwareBackedKeyPair {
        let seKeySpec = SeKeySpec(
            name: "bitkey-noise-ephemeral-key-\(name)",
            purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
            usageConstraints: SeKeyUsageConstraints.none,
            validity: nil
        )
        let seKeyPair = try secureEnclave.generateP256KeyPair(spec: seKeySpec)
        let privkeyName = seKeyPair.privateKey.name
        let pubkeyBytes = seKeyPair.publicKey.bytes.asData()
        return core.HardwareBackedKeyPair(privkeyName: privkeyName, pubkey: pubkeyBytes)
    }

    func pubkey(privkeyName: String) throws -> Data {
        let sePrivateKeyHandle = SeKeyHandle(name: privkeyName)
        let sePublicKey = try secureEnclave.publicKeyForPrivateKey(sePrivateKey: sePrivateKeyHandle)
        return sePublicKey.bytes.asData()
    }
}
