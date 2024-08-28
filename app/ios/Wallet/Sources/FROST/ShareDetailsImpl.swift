import core
import Foundation
import Shared

public class ShareDetailsImpl: Shared.ShareDetails {
    let coreShareDetails: core.ShareDetails

    public init(coreShareDetails: core.ShareDetails) {
        self.coreShareDetails = coreShareDetails
    }
}
