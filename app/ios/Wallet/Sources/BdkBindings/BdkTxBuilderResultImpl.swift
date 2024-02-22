import BitcoinDevKit
import Shared

class BdkTxBuilderResultImpl : BdkTxBuilderResult {
    
    private let txBuilderResult: TxBuilderResult
    
    init(txBuilderResult: TxBuilderResult) {
        self.txBuilderResult = txBuilderResult
    }
    
    var psbt: BdkPartiallySignedTransaction {
        BdkPartiallySignedTransactionImpl(ffiPsbt: txBuilderResult.psbt)
    }
    
    func destroy() {
        // noop
    }
}
