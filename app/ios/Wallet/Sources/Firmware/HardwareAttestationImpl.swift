import firmware
import Foundation
import Shared

public final class HardwareAttestationImpl: Shared.HardwareAttestation {
    public init() {}
    
    public func generateChallenge() throws -> [KotlinUByte] {
        return try firmware.Attestation().generateChallenge().map {
            KotlinUByte(unsignedChar: $0)
        }
    }
    
    public func verifyCertChain(identityCert: [KotlinUByte], batchCert: [KotlinUByte]) throws -> String {
        return try firmware.Attestation().verifyDeviceIdentityCertChain(
            identityCertDer: identityCert.map { $0.uint8Value },
            batchCertDer: batchCert.map { $0.uint8Value }
        )
    }
}
