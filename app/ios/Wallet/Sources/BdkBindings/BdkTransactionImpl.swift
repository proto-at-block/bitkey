import BitcoinDevKitLegacy
import Shared

class BdkTransactionImpl: BdkTransaction {

    let ffiTransaction: FfiTransaction

    public init(ffiTransaction: FfiTransaction) {
        self.ffiTransaction = ffiTransaction
    }

    public func serialize() -> [KotlinUByte] {
        ffiTransaction.serialize().map { KotlinUByte(value: $0) }
    }

    public func txid() -> String {
        ffiTransaction.txid()
    }

    public func size() -> UInt64 {
        return ffiTransaction.size()
    }

    public func vsize() -> UInt64 {
        return ffiTransaction.vsize()
    }

    public func weight() -> UInt64 {
        return ffiTransaction.weight()
    }

    public func input() -> [BdkTxIn] {
        ffiTransaction.input().map { $0.bdkTxIn() }
    }

    public func output() -> [BdkTxOut] {
        ffiTransaction.output().map { $0.bdkTxOut() }
    }

}

private extension TxIn {
    func bdkTxIn() -> BdkTxIn {
        return BdkTxIn(
            outpoint: self.previousOutput.toBdkOutPoint(),
            sequence: self.sequence,
            witness: self.witness.map { $0.map { KotlinUByte(value: $0) } }
        )
    }
}

private extension TxOut {
    func bdkTxOut() -> BdkTxOut {
        return BdkTxOut(value: value, scriptPubkey: BdkScriptImpl(ffiScript: scriptPubkey))
    }
}
