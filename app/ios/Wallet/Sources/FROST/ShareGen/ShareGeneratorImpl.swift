import core
import Foundation
import Shared

public class ShareGeneratorImpl: Shared.ShareGenerator {
    private let coreShareGenerator: core.ShareGenerator = .init()

    public func generate() -> KeygenResult<Shared.UnsealedRequest> {
        return KeygenResult {
            try UnsealedRequest(value: coreShareGenerator.generate())
        }
    }

    public func aggregate(
        unsealedRequest request: Shared.UnsealedRequest
    ) -> KeygenResult<Shared.ShareDetails> {
        return KeygenResult {
            try ShareDetailsImpl(
                coreShareDetails: coreShareGenerator.aggregate(
                    sealedResponse: request.value
                )
            )
        }
    }

    public func encode(
        shareDetails: Shared.ShareDetails
    ) -> KeygenResult<Shared.UnsealedRequest> {
        let realShareDetails = shareDetails as! ShareDetailsImpl
        return KeygenResult {
            try UnsealedRequest(
                value: coreShareGenerator
                    .encodeCompleteDistributionRequest(
                        shareDetails: realShareDetails
                            .coreShareDetails
                    )
            )
        }
    }
}
