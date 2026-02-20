import core
import Foundation
import Shared

class ChaincodeExtractorImpl: Shared.ChaincodeExtractor {
    func extractChaincode(xpub: String) -> Shared.ChaincodeDelegationResult<OkioByteString> {
        return ChaincodeDelegationResult {
            let chaincodeBytes = try core.extractXpubChaincode(xpub: xpub)
            let data = Data(chaincodeBytes)
            return OkioKt.ByteString(data: data)
        }
    }
}
