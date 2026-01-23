import Shared

/**
 * Helper class to wrap Swift async closures as Kotlin suspend functions.
 * Used for W3 confirmation protocol where callbacks receive both NfcSession and NfcCommands.
 */
final class NfcSessionSuspendFunction: Shared.KotlinSuspendFunction2 {
    private let closure: (Shared.NfcSession, Shared.NfcCommands) async throws -> Shared
        .HardwareInteraction

    init(
        _ closure: @escaping (Shared.NfcSession, Shared.NfcCommands) async throws -> Shared
            .HardwareInteraction
    ) {
        self.closure = closure
    }

    func invoke(p1: Any?, p2: Any?) async throws -> Any? {
        guard let session = p1 as? Shared.NfcSession else {
            throw NSError(domain: "NfcSessionSuspendFunction", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Invalid session parameter",
            ])
        }
        guard let commands = p2 as? Shared.NfcCommands else {
            throw NSError(domain: "NfcSessionSuspendFunction", code: -2, userInfo: [
                NSLocalizedDescriptionKey: "Invalid commands parameter",
            ])
        }
        return try await closure(session, commands)
    }
}
