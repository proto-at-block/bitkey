import Shared

/**
 * Helper class to wrap Swift async closures as Kotlin suspend functions for transfer operations.
 * Used for W3 chunked data transfer where callbacks receive NfcSession, NfcCommands, and a progress callback.
 */
final class NfcSessionTransferFunction: Shared.KotlinSuspendFunction3 {
    private let closure: (
        Shared.NfcSession,
        Shared.NfcCommands,
        @escaping (Float) -> Void
    ) async throws -> Shared.HardwareInteraction

    init(
        _ closure: @escaping (
            Shared.NfcSession,
            Shared.NfcCommands,
            @escaping (Float) -> Void
        ) async throws -> Shared.HardwareInteraction
    ) {
        self.closure = closure
    }

    func invoke(p1: Any?, p2: Any?, p3: Any?) async throws -> Any? {
        guard let session = p1 as? Shared.NfcSession else {
            throw NSError(domain: "NfcSessionTransferFunction", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Invalid session parameter",
            ])
        }
        guard let commands = p2 as? Shared.NfcCommands else {
            throw NSError(domain: "NfcSessionTransferFunction", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "Invalid commands parameter",
            ])
        }
        guard let onProgress = p3 as? (Float) -> Void else {
            throw NSError(domain: "NfcSessionTransferFunction", code: -3, userInfo: [
                NSLocalizedDescriptionKey: "Invalid onProgress parameter",
            ])
        }
        return try await closure(session, commands, onProgress)
    }
}
