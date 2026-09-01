import core
import Foundation
import Shared

public final class SignatureVerifierImpl: Shared.SignatureVerifier {

    public init() {}

    public func verifyEcdsa(
        message: OkioByteString,
        signature: String,
        publicKey: Secp256k1PublicKey
    ) throws -> SignatureVerifierVerifyEcdsaResult {
        let decodedSignature = try OkioKt.decodeHex(s: signature)
        let verifier = try core.SignatureVerifier(signature: decodedSignature)
        // Must use the @Throws helper: a Kotlin exception from a non-@Throws function called
        // from Swift cannot unwind through Swift frames and terminates the process.
        let decodedPublicKey = try OkioKt.decodeHex(s: publicKey.value)
        try verifier.verifyEcdsa(message: message.toData(), pubkey: decodedPublicKey)

        return SignatureVerifierVerifyEcdsaResult(isValid: true)
    }
}
