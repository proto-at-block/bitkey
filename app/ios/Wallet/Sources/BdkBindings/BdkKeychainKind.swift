import BitcoinDevKit
import Shared

extension BdkKeychainKind {

    var ffiKeychainKind : KeychainKind {
        switch self {
        case BdkKeychainKind.external: return .external
        case BdkKeychainKind.internal: return .internal
        default: fatalError()
        }
    }

}
