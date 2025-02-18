import core
import Foundation
import Shared

extension Shared.SigningError {

    /**
     * Maps `KeygenError` type from `core` to KMP `Shared.KeygenError` type.
     */
    static func create(_ error: Error) -> Shared.SigningError {
        let coreSigningError = error as! core.SigningError
        // Convert Swift Error to Kotlin Throwable.
        let throwable = NSErrorKt.asThrowable(error)

        switch coreSigningError {
        case let .InvalidPsbt(message: message):
            return InvalidPsbt(cause: throwable, message: message)
        case let .UnableToRetrieveSighash(message: message):
            return UnableToRetrieveSighash(cause: throwable, message: message)
        case let .InvalidCounterpartyCommitments(message: message):
            return InvalidCounterpartyCommitments(cause: throwable, message: message)
        case let .NonceAlreadyUsed(message: message):
            return NonceAlreadyUsed(cause: throwable, message: message)
        case let .CommitmentMismatch(message: message):
            return CommitmentMismatch(cause: throwable, message: message)
        case let .MissingCounterpartyNonces(message: message):
            return MissingCounterpartyNonces(cause: throwable, message: message)
        case let .UnableToFinalizePsbt(message: message):
            return UnableToFinalizePsbt(cause: throwable, message: message)
        }
    }
}
