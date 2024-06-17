import Foundation
import Shared

public final class XNonceGeneratorImpl: XNonceGenerator {
    public init() {}

    public func generateXNonce() throws -> Shared.XNonce {
        var bytes = [UInt8](repeating: 0, count: 24)
        let status = SecRandomCopyBytes(
            kSecRandomDefault,
            24,
            &bytes
        )
        guard status == errSecSuccess else {
            throw Shared.XNonceGeneratorError
                .XNonceGenerationError(message: "SecRandomCopyBytes failed with status: \(status)")
                .asError()
        }
        return Shared.XNonce(bytes: OkioKt.ByteString(data: Data(bytes)))
    }
}
