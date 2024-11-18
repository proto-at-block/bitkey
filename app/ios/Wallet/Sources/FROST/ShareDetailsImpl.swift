import core
import Foundation
import Shared

public class ShareDetailsImpl: Shared.ShareDetails {
    let coreShareDetails: core.ShareDetails

    public let secretShare: [KotlinUByte]
    public let keyCommitments: Shared.KeyCommitments

    public init(coreShareDetails: core.ShareDetails) {
        self.coreShareDetails = coreShareDetails
        self.secretShare = coreShareDetails.secretShare.map { KotlinUByte(value: $0) }
        self
            .keyCommitments = KeyCommitmentsImpl(
                coreKeyCommitments: coreShareDetails
                    .keyCommitments
            )
    }
}
