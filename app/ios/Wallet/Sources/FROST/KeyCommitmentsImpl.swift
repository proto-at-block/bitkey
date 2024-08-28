import core
import Foundation
import Shared

public class KeyCommitmentsImpl: Shared.KeyCommitments {
    let coreKeyCommitments: core.KeyCommitments

    public init(coreKeyCommitments: core.KeyCommitments) {
        self.coreKeyCommitments = coreKeyCommitments
    }
}
