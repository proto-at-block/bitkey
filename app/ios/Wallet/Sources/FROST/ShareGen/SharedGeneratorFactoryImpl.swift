import core
import Foundation
import Shared

public class ShareGeneratorFactoryImpl: Shared.ShareGeneratorFactory {

    public func createShareGenerator() -> Shared.ShareGenerator {
        return ShareGeneratorImpl()
    }
}
