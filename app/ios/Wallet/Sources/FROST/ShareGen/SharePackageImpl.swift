import core
import Foundation
import Shared

public class SharePackageImpl: Shared.SharePackage {
    let coreSharePackage: core.SharePackage

    public init(coreSharePackage: core.SharePackage) {
        self.coreSharePackage = coreSharePackage
    }
}
