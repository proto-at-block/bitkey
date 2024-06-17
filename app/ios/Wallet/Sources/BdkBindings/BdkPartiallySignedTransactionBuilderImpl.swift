import BitcoinDevKit
import Shared

public class BdkPartiallySignedTransactionBuilderImpl: BdkPartiallySignedTransactionBuilder {

    public init() {}

    public func build(psbtBase64: String) -> BdkResult<BdkPartiallySignedTransaction> {
        return BdkResult {
            try BdkPartiallySignedTransactionImpl(
                ffiPsbt: PartiallySignedTransaction(psbtBase64: psbtBase64)
            )
        }
    }
}
