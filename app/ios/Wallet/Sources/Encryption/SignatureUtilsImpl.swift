import core
import Foundation
import Shared

public class SignatureUtilsImpl: SignatureUtils {
    public init() {}

    public func encodeSignatureToDer(compactSignature: KotlinByteArray) throws -> OkioByteString {
        let derBytes = try core.encodeSignatureToDer(compactSignature: compactSignature.asData())
        return OkioKt.ByteString(data: Data(derBytes))
    }
}
