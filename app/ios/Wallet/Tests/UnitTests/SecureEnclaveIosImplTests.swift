import Foundation
import Shared
import XCTest

@testable import Wallet

private extension Data {
    func hexEncodedString() -> String {
        return map { String(format: "%02hhx", $0) }.joined()
    }
}

private extension String {
    func hexToByteArray() -> [UInt8] {
        var bytes = [UInt8]()
        var index = startIndex
        while index < endIndex {
            let nextIndex = self.index(index, offsetBy: 2)
            if let byte = UInt8(self[index ..< nextIndex], radix: 16) {
                bytes.append(byte)
            }
            index = nextIndex
        }
        return bytes
    }
}

class SecureEnclaveIosImplTests: XCTestCase {
    func testGenerateP256KeyPair() throws {
        let se = SecureEnclaveImpl()
        let spec = SeKeySpec(
            name: "test-key",
            purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
            usageConstraints: SeKeyUsageConstraints.none,
            validity: nil
        )

        let key = try se.generateP256KeyPair(spec: spec)
        print("Key: \(key.publicKey.bytes.asData().hexEncodedString())")
    }

    func testDiffieHellman() throws {
        let se = SecureEnclaveImpl()

        let clientKeySpec = SeKeySpec(
            name: "key-testDiffieHellman-client",
            purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
            usageConstraints: SeKeyUsageConstraints.none,
            validity: nil
        )

        let clientKeyPair = try se.generateP256KeyPair(spec: clientKeySpec)
        let clientPublicKey = try se.publicKeyForPrivateKey(sePrivateKey: clientKeyPair.privateKey)

        let serverKeySpec = SeKeySpec(
            name: "key-testDiffieHellman-server",
            purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
            usageConstraints: SeKeyUsageConstraints.none,
            validity: nil
        )
        let serverKeyPair = try se.generateP256KeyPair(spec: serverKeySpec)
        let serverPublicKey = try se.publicKeyForPrivateKey(sePrivateKey: serverKeyPair.privateKey)

        let sharedSecret1 = try se.diffieHellman(
            ourPrivateKey: clientKeyPair.privateKey,
            peerPublicKey: serverPublicKey
        )
        let sharedSecret2 = try se.diffieHellman(
            ourPrivateKey: serverKeyPair.privateKey,
            peerPublicKey: clientPublicKey
        )

        print("Shared secret 1: \(sharedSecret1.asData().hexEncodedString())")
        print("Shared secret 2: \(sharedSecret2.asData().hexEncodedString())")

        XCTAssertEqual(sharedSecret1.asData(), sharedSecret2.asData())
    }

    #if false
        // This code can't be ran from a unit test because testing usageConstraints requires
        // ability to use the Secure Enclave. Copy and paste this into the actual app to test it
        // fully.
        func testKeyUsageConstraints() throws {
            let se = SecureEnclaveImpl()

            let keySpec = SeKeySpec(
                name: "key-testKeyUsageConstraints",
                purposes: SeKeyPurposes(purposes: [SeKeyPurpose.agreement]),
                usageConstraints: SeKeyUsageConstraints.biometricsOrPinRequired,
                validity: nil
            )

            let keyPair = try se.generateP256KeyPair(spec: keySpec)

            try se.diffieHellman(
                ourPrivateKey: keyPair.privateKey,
                peerPublicKey: keyPair.publicKey
            )
        }
    #endif

    func testLoadPublicKey() throws {
        let sec1Pubkey =
            "04e952c94ecd6d4438edaf8939f9164533dedb1c6e822534f800f60f3a116054f47c783dca8bc5193e4cb71c870c0696f7d3d9ed9716413cfeb293f879ee8a9e73"
        let se = SecureEnclaveImpl()
        let pubkey = try se
            .loadSePublicKey(SePublicKey(bytes: KotlinByteArray(sec1Pubkey.hexToByteArray())))

        // Get the pubkey back out
        var error: Unmanaged<CFError>?
        let pubkeyBytes = SecKeyCopyExternalRepresentation(pubkey, &error)! as Data

        XCTAssertEqual(sec1Pubkey, pubkeyBytes.hexEncodedString())
    }
}
