import core
import Foundation
import Shared

class FrostSignerFactoryImpl: Shared.FrostSignerFactory {
    public func create(
        psbt: String,
        shareDetails: Shared.ShareDetails
    ) -> SigningResult<Shared.FrostSigner> {
        let realShareDetails = shareDetails as! ShareDetailsImpl
        return SigningResult {
            try FrostSignerImpl(psbt: psbt, shareDetails: realShareDetails.coreShareDetails)
        }
    }
}
