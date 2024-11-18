import core
import Foundation
import Shared

public class KeyCommitmentsImpl: Shared.KeyCommitments {
    let coreKeyCommitments: core.KeyCommitments

    public let vssCommitments: [Shared.PublicKey]
    public let aggregatePublicKey: Shared.PublicKey

    public init(coreKeyCommitments: core.KeyCommitments) {
        self.coreKeyCommitments = coreKeyCommitments
        self.vssCommitments = coreKeyCommitments.vssCommitments
            .map { PublicKeyImpl(corePublicKey: $0) }
        self
            .aggregatePublicKey = PublicKeyImpl(
                corePublicKey: coreKeyCommitments
                    .aggregatePublicKey
            )
    }
}
