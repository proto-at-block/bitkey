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

    func allowShrinking(script: BdkScript) -> BdkBumpFeeTxBuilder {
        let realBdkScript = script as! BdkScriptImpl

        return BdkBumpFeeTxBuilderImpl(
            txBuilder: txBuilder
                .allowShrinking(scriptPubkey: realBdkScript.toFfiScript())
        )
    }

    func finish(wallet: BdkWallet) -> BdkResult<BdkPartiallySignedTransaction> {
        let realBdkWallet = wallet as! BdkWalletImpl

        return BdkResult {
            try BdkPartiallySignedTransactionImpl(
                ffiPsbt: self.txBuilder
                    .finish(wallet: realBdkWallet.wallet)
            )
        }
    }
}
