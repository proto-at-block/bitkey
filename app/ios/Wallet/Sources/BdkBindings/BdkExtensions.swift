import BitcoinDevKitLegacy
import Foundation
import Shared

extension FfiOutPoint {
    func toBdkOutPoint() -> BdkOutPoint {
        BdkOutPoint(txid: self.txid, vout: self.vout)
    }
}

extension FfiTxOut {
    func toBdkTxOut() -> BdkTxOut {
        BdkTxOut(value: self.value, scriptPubkey: self.scriptPubkey.toBdkScript())
    }
}

extension FfiScript {
    func toBdkScript() -> BdkScriptImpl {
        BdkScriptImpl(ffiScript: self)
    }
}

extension FfiTransaction {
    func toBdkTransaction() -> BdkTransactionImpl {
        BdkTransactionImpl(ffiTransaction: self)
    }
}
