import core
import Foundation
import Shared

extension Shared.ChaincodeDelegationError {
    /**
     * Maps `ChaincodeDelegationError` type from `core` to KMP `Shared.ChaincodeDelegationError` type.
     */
    static func create(_ error: Error) -> Shared.ChaincodeDelegationError {
        let coreSigningError = error as! core.ChaincodeDelegationError
        // Convert Swift Error to Kotlin Throwable.
        let throwable = NSErrorKt.asThrowable(error)

        switch coreSigningError {
        case let .KeyDerivation(reason: reason):
            return KeyDerivation(cause: throwable, message: reason)
        case let .TweakComputation(reason: reason):
            return TweakComputation(cause: throwable, message: reason)
        case let .UnknownKey(fingerprint: fingerprint):
            return UnknownKey(cause: throwable, message: "Unknown fingerprint: \(fingerprint)")
        case let .KeyMismatch(expected: expected, actual: actual):
            return KeyMismatch(
                cause: throwable,
                message: "Expected: \(expected), Actual: \(actual)"
            )
        }
    }
}
