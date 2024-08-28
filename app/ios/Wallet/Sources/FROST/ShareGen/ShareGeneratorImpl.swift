import core
import Foundation
import Shared

public class ShareGeneratorImpl: Shared.ShareGenerator {
    private let coreShareGenerator: core.ShareGenerator

    public init(coreShareGenerator: core.ShareGenerator) {
        self.coreShareGenerator = coreShareGenerator
    }

    public func generate() -> KeygenResult<Shared.SharePackage> {
        return KeygenResult {
            try SharePackageImpl(coreSharePackage: coreShareGenerator.generate())
        }
    }

    public func aggregate(
        peerSharePackage: Shared.SharePackage,
        peerKeyCommitments: Shared.KeyCommitments
    ) -> KeygenResult<Shared.ShareDetails> {
        let peerSharePackage = peerSharePackage as! SharePackageImpl
        let peerKeyCommitments = peerKeyCommitments as! KeyCommitmentsImpl

        return KeygenResult {
            try ShareDetailsImpl(
                coreShareDetails: coreShareGenerator.aggregate(
                    peerSharePackage: peerSharePackage.coreSharePackage,
                    peerKeyCommitments: peerKeyCommitments.coreKeyCommitments
                )
            )
        }
    }
}
