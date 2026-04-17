import Shared

/**
 * Bridges a Swift throwing closure as a Kotlin `ConfirmationResultMapper`.
 *
 * Replaces the old pattern where Swift command implementations had to wrap
 * an `async throws` closure as `KotlinSuspendFunction2` and store it in Kotlin
 * state to be invoked across the coroutine boundary on the second NFC tap.
 *
 * With this approach the state machine calls `getConfirmationResult` directly in
 * Kotlin, then passes the plain `ConfirmationResult` value here for synchronous
 * mapping — no coroutine crossing involved.
 */
final class NfcConfirmationResultMapper: Shared.ConfirmationResultMapper {
    private let closure: (Shared.ConfirmationResult) throws -> Shared.HardwareInteraction

    init(_ closure: @escaping (Shared.ConfirmationResult) throws -> Shared.HardwareInteraction) {
        self.closure = closure
    }

    func mapResult(result: Shared.ConfirmationResult) throws -> Shared.HardwareInteraction {
        do {
            return try closure(result)
        } catch {
            let nsError = error as NSError
            if nsError.isKotlinException {
                // NSError produced by a Kotlin exception via .asError() — forward as-is
                // so the KMM bridge reconstructs the original Kotlin exception on the
                // receiving side (e.g. NfcException.ConfirmationPending or UserDenied).
                throw error
            }
            // Pure Swift error with no Kotlin origin — normalize to NfcException.CommandError
            // so Kotlin callers always receive a typed exception.
            throw NfcException.CommandError(
                message: error.localizedDescription,
                cause: nil
            ).asError()
        }
    }
}
