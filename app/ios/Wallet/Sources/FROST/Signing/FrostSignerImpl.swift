import core
import Foundation
import Shared

class FrostSignerImpl: Shared.FrostSigner {
    private let coreFrostSigner: core.FrostSigner

    init(psbt: core.Psbt, shareDetails: core.ShareDetails) throws {
        do {
            coreFrostSigner = try core.FrostSigner(psbt: psbt, shareDetails: shareDetails)
        } catch {
            throw error
        }
    }

    func generateSealedSignPsbtRequest() -> SigningResult<NSString> {
        return SigningResult {
            try coreFrostSigner.signPsbtRequest() as NSString
        }
    }

    func signPsbt(unsealedResponse: UnsealedResponse) -> SigningResult<NSString> {
        return SigningResult {
            try coreFrostSigner.signPsbt(sealedResponse: unsealedResponse.value) as NSString
        }
    }
}
