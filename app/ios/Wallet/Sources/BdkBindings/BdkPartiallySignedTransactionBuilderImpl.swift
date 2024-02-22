import BitcoinDevKit
import Shared

public class BdkPartiallySignedTransactionBuilderImpl : BdkPartiallySignedTransactionBuilder {
    
    public init() {}
    
    public func build(psbtBase64: String) -> BdkResult<BdkPartiallySignedTransaction> {
        return BdkResult {
            BdkPartiallySignedTransactionImpl(
                ffiPsbt: try PartiallySignedTransaction(psbtBase64: psbtBase64)
            )
        }
    }
}
