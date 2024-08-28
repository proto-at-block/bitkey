import core
import Foundation
import Shared

extension Shared.KeygenError {

    /**
     * Maps `KeygenError` type from `core` to KMP `Shared.KeygenError` type.
     */
    static func create(_ error: Error) -> Shared.KeygenError {
        let coreKeygenError = error as! core.KeygenError
        // Convert Swift Error to Kotlin Throwable.
        let throwable = NSErrorKt.asThrowable(error)

        switch coreKeygenError {
        case let .MissingSharePackage(message: message):
            return MissingSharePackage(cause: throwable, message: message)
        case let .MissingShareAggParams(message: message):
            return MissingSharePackage(cause: throwable, message: message)
        case let .InvalidParticipantIndex(message: message):
            return InvalidParticipantIndex(cause: throwable, message: message)
        case let .InvalidProofOfKnowledge(message: message):
            return InvalidProofOfKnowledge(cause: throwable, message: message)
        case let .InvalidIntermediateShare(message: message):
            return InvalidIntermediateShare(cause: throwable, message: message)
        case let .InvalidKeyCommitments(message: message):
            return InvalidKeyCommitments(cause: throwable, message: message)
        }
    }
}
