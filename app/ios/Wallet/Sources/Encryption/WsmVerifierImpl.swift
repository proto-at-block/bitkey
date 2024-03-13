import Foundation
import Shared
import core


class WsmVerifierImpl: Shared.WsmVerifier {
    public init () {}
    
    func verify(base58Message: String, signature: String, keyVariant: WsmIntegrityKeyVariant) throws -> WsmVerifierResult {
        let verifier = core.WsmIntegrityVerifier(pubkey: keyVariant.pubkey)
        return try Shared.WsmVerifierResult(isValid: verifier.verify(base58Message: base58Message, signature: signature))
    }
}
