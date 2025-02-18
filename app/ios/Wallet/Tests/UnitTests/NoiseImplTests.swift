import core
import CryptoKit
import Shared
import XCTest

@testable import Wallet

class NoiseIosTests: XCTestCase {

    var se: SecureEnclaveImpl!

    override func setUp() {
        super.setUp()
        se = SecureEnclaveImpl()
    }

    func testNoise() throws {
        // Generate the client key pair
        let clientKeyPair = try se.generateP256KeyPair(
            spec: SeKeySpec(
                name: "key-testNoise-client",
                purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
                usageConstraints: SeKeyUsageConstraints.none,
                validity: nil
            )
        )

        // Generate the server key pair
        let serverKeyPair = try se.generateP256KeyPair(
            spec: SeKeySpec(
                name: "key-testNoise-server",
                purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
                usageConstraints: SeKeyUsageConstraints.none,
                validity: nil
            )
        )
        let serverPublicKey = try se.publicKeyForPrivateKey(sePrivateKey: serverKeyPair.privateKey)

        // Create Diffie-Hellman implementations for the client and server
        let hardwareBackedDhClient = CoreHardwareBackedDhImpl(secureEnclave: se, name: "client")
        let hardwareBackedDhServer = CoreHardwareBackedDhImpl(secureEnclave: se, name: "server")

        // Initialize Noise context for client and server
        let clientNoiseContext = try NoiseContextImpl(
            role: NoiseRole.initiator,
            privateKey: core.PrivateKey.hardwareBacked(name: clientKeyPair.privateKey.name),
            theirPublicKey: serverPublicKey.bytes.asData(),
            dh: hardwareBackedDhClient
        )

        let serverNoiseContext = try NoiseContextImpl(
            role: NoiseRole.responder,
            privateKey: core.PrivateKey.hardwareBacked(name: serverKeyPair.privateKey.name),
            theirPublicKey: nil,
            dh: hardwareBackedDhServer
        )

        // Perform Noise handshake
        let c2s = try clientNoiseContext.initiateHandshake()
        let s2c = try serverNoiseContext.advanceHandshake(peerHandshakeMessage: c2s)
        let _ = try clientNoiseContext.advanceHandshake(peerHandshakeMessage: s2c!)

        // Finalize the handshake
        try clientNoiseContext.finalizeHandshake()
        try serverNoiseContext.finalizeHandshake()

        // Test message encryption and decryption after the handshake
        let clientMessage = "client -> server".data(using: .utf8)!
        let serverMessage = "server -> client".data(using: .utf8)!

        let encryptedClientMessage = try clientNoiseContext.encryptMessage(message: clientMessage)
        let decryptedClientMessage = try serverNoiseContext
            .decryptMessage(ciphertext: encryptedClientMessage)
        XCTAssertEqual(clientMessage, decryptedClientMessage)

        let encryptedServerMessage = try serverNoiseContext.encryptMessage(message: serverMessage)
        let decryptedServerMessage = try clientNoiseContext
            .decryptMessage(ciphertext: encryptedServerMessage)
        XCTAssertEqual(serverMessage, decryptedServerMessage)
    }
}
