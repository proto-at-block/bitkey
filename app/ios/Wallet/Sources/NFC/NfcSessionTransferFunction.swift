import Shared

/**
 * Helper class to wrap Swift async closures as Kotlin suspend functions for transfer operations.
 * Used for W3 chunked data transfer where callbacks receive NfcSession, NfcCommands, and a progress callback.
 *
 * The progress callback (p3) arrives as a Kotlin `NfcProgressCallback` fun interface, which is
 * properly exported as an ObjC protocol. This avoids the Kotlin/Native interop issue where
 * raw function types `(Float) -> Unit` passed as `Any?` through `KotlinSuspendFunction3.invoke`
 * cannot be cast to Swift closures.
 */
final class NfcSessionTransferFunction: Shared.KotlinSuspendFunction3 {
    private let closure: (
        Shared.NfcSession,
        Shared.NfcCommands,
        Shared.NfcProgressCallback
    ) async throws -> Shared.HardwareInteraction

    init(
        _ closure: @escaping (
            Shared.NfcSession,
            Shared.NfcCommands,
            Shared.NfcProgressCallback
        ) async throws -> Shared.HardwareInteraction
    ) {
        self.closure = closure
    }

    func invoke(p1: Any?, p2: Any?, p3: Any?) async throws -> Any? {
        guard let session = p1 as? Shared.NfcSession else {
            throw NfcException.UnknownError(
                message: "Invalid session parameter",
                cause: nil
            ).asError()
        }
        guard let commands = p2 as? Shared.NfcCommands else {
            throw NfcException.UnknownError(
                message: "Invalid commands parameter",
                cause: nil
            ).asError()
        }
        guard let onProgress = p3 as? Shared.NfcProgressCallback else {
            throw NfcException.UnknownError(
                message: "Invalid onProgress parameter: \(type(of: p3))",
                cause: nil
            ).asError()
        }
        do {
            return try await closure(session, commands, onProgress)
        } catch {
            // Ensure Kotlin exceptions are properly bridged back
            // Swift async errors need explicit handling for Kotlin interop
            if let nsError = error as? NSError, nsError.isKotlinException {
                throw nsError
            }
            // Re-wrap as NfcException to ensure proper Kotlin bridging
            throw NfcException.UnknownError(
                message: error.localizedDescription,
                cause: nil
            ).asError()
        }
    }
}
