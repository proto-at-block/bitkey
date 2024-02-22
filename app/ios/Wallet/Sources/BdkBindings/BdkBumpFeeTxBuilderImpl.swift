import BitcoinDevKit
import Shared

class BdkBumpFeeTxBuilderImpl: BdkBumpFeeTxBuilder {
    private let txBuilder: BumpFeeTxBuilder
    
    public init(txBuilder: BumpFeeTxBuilder) {
        self.txBuilder = txBuilder
    }
    
    func enableRbf() -> BdkBumpFeeTxBuilder {
        return BdkBumpFeeTxBuilderImpl(txBuilder: txBuilder.enableRbf())
    }
    
    func finish(wallet: BdkWallet) -> BdkResult<BdkPartiallySignedTransaction> {
        let realBdkWallet = wallet as! BdkWalletImpl
        
        return BdkResult {
            BdkPartiallySignedTransactionImpl(ffiPsbt: try self.txBuilder.finish(wallet: realBdkWallet.wallet))
        }
    }
}
