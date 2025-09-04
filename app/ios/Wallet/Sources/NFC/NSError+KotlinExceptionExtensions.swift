import Foundation
import Shared

extension NSError {
    private static let kotlinExceptionDomain = "KotlinException"
    private static let kotlinExceptionKey = "KotlinException"
    private static let generalErrorMessage = "command was unsuccessful: general error"

    func isKotlinNfcCommandError(containing message: String) -> Bool {
        guard let kotlinException: NfcException.CommandError = kotlinException(),
              let errorMessage = kotlinException.message
        else {
            return false
        }
        return errorMessage.contains(message)
    }

    func isKotlinNfcFileNotFoundError() -> Bool {
        return kotlinException() as NfcException.CommandErrorFileNotFound? != nil
    }

    func isKotlinNfcGeneralError() -> Bool {
        return isKotlinNfcCommandError(containing: Self.generalErrorMessage)
    }

    private func kotlinException<T>() -> T? {
        guard domain == Self.kotlinExceptionDomain else { return nil }
        return userInfo[Self.kotlinExceptionKey] as? T
    }
}
