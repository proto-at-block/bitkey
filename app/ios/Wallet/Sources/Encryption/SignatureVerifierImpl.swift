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
        let decodedPublicKey = OkioByteString.companion.decodeHex(publicKey.value).toData()
        try verifier.verifyEcdsa(message: message.toData(), pubkey: decodedPublicKey)

        return SignatureVerifierVerifyEcdsaResult(isValid: true)
    }
}
