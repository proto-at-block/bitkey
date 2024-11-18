import core
import Foundation
import Shared

public class ShareGeneratorImpl: Shared.ShareGenerator {
    private let coreShareGenerator: core.ShareGenerator = .init()

    public func generate() -> KeygenResult<Shared.SealedRequest> {
        return KeygenResult {
            try SealedRequest(value: coreShareGenerator.generate())
        }
    }

    public func aggregate(
        sealedRequest: Shared.SealedRequest
    ) -> KeygenResult<Shared.ShareDetails> {
        return KeygenResult {
            try ShareDetailsImpl(
                coreShareDetails: coreShareGenerator.aggregate(
                    sealedResponse: sealedRequest.value
                )
            )
        }
    }

    public func encode(
        shareDetails: Shared.ShareDetails
    ) -> KeygenResult<Shared.SealedRequest> {
        let realShareDetails = shareDetails as! ShareDetailsImpl
        return KeygenResult {
            try SealedRequest(
                value: coreShareGenerator
                    .encodeCompleteDistributionRequest(
                        shareDetails: realShareDetails
                            .coreShareDetails
                    )
            )
        }
    }
}
