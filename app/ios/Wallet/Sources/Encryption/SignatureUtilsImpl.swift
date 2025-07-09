import core
import Foundation
import Shared

public class SignatureUtilsImpl: SignatureUtils {
    public init() {}

    public func encodeSignatureToDer(compactSignature: KotlinByteArray) throws -> OkioByteString {
        let derBytes = try core
            .compactSignatureToDer(compactSignature: compactSignature.asUInt8Array())
        return OkioKt.ByteString(data: Data(derBytes))
    }

    public func decodeSignatureFromDer(derSignature: OkioByteString) throws -> KotlinByteArray {
        let compactBytes = try core
            .compactSignatureFromDer(derSignature: derSignature.toByteArray().asUInt8Array())
        return KotlinByteArray(compactBytes)
    }
}
