import core
import Foundation
import Shared

public final class HardwareAttestationImpl: Shared.HardwareAttestation {
    public init() {}
    
    public func generateChallenge() throws -> [KotlinUByte] {
        return try core.Attestation().generateChallenge().map {
            KotlinUByte(unsignedChar: $0)
        }
    }
    
    public func verifyCertChain(identityCert: [KotlinUByte], batchCert: [KotlinUByte]) throws -> String {
        return try core.Attestation().verifyDeviceIdentityCertChain(
            identityCertDer: identityCert.map { $0.uint8Value },
            batchCertDer: batchCert.map { $0.uint8Value }
        )
    }
}
