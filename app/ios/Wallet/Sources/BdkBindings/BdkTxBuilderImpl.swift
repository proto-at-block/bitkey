import BitcoinDevKit
import Shared

class BdkTxBuilderImpl : BdkTxBuilder {
    func feeAbsolute(fee: Int64) -> BdkTxBuilder {
        return BdkTxBuilderImpl(
            txBuilder: txBuilder.feeAbsolute(feeAmount: UInt64(fee))
        )
    }
    

    private let txBuilder: TxBuilder
        
    public init(txBuilder: TxBuilder) {
        self.txBuilder = txBuilder
    }
    
    func addRecipient(script: BdkScript, amount: BignumBigInteger) -> BdkTxBuilder {
        let realBdkScript = script as! BdkScriptImpl
        return BdkTxBuilderImpl(
            txBuilder: txBuilder.addRecipient(
                script: realBdkScript.ffiScript,
                amount: amount.ulongValue(exactRequired: true)
            )
        )
    }
    
    func drainTo(address: BdkAddress) -> BdkTxBuilder {
        let realBdkScript = address.scriptPubkey() as! BdkScriptImpl
        return BdkTxBuilderImpl(
            txBuilder: txBuilder.drainTo(script: realBdkScript.ffiScript)
        )
    }
    
    func drainWallet() -> BdkTxBuilder {
        return BdkTxBuilderImpl(txBuilder: txBuilder.drainWallet())
    }
    
    func feeRate(satPerVbyte: Float) -> BdkTxBuilder {
        return BdkTxBuilderImpl(txBuilder: txBuilder.feeRate(satPerVbyte: satPerVbyte))
    }
    
    func enableRbf() -> BdkTxBuilder {
        return BdkTxBuilderImpl(txBuilder: txBuilder.enableRbf())
    }
    
    func finish(wallet: BdkWallet) -> BdkResult<BdkTxBuilderResult> {
        let realBdkWallet = wallet as! BdkWalletImpl
        
        return BdkResult {
            BdkTxBuilderResultImpl(txBuilderResult: try txBuilder.finish(wallet: realBdkWallet.wallet))
        }
    }
}
