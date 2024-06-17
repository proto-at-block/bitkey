import core
import Foundation
import Shared

public final class HkdfImpl: Shared.Hkdf {
    public init() {}

    public func deriveKey(
        ikm: OkioByteString,
        salt: OkioByteString?,
        info: OkioByteString?,
        outputLength: Int32
    ) -> Shared.SymmetricKey {
        let hkdf = core.Hkdf(
            // Use a zero-filled byte sequence if salt is null (see RFC 5869, Section 2.2).
            // The length of the salt is set to 32 bytes to match the length of the output
            // of the SHA-256 hash function.
            salt: salt?.toData() ?? Data(repeating: 0, count: 32),
            ikm: ikm.toData()
        )
        let okm = try! hkdf.expand(
            // Use a zero-length byte sequence if info is null (see RFC 5869, Section 2.3).
            info: info?.toData() ?? Data(),
            len: outputLength
        )
        return Shared.SymmetricKeyKt.SymmetricKey(data: Data(okm))
    }
}
