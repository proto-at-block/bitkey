import BitcoinDevKitLegacy
import Shared

public class BdkPartiallySignedTransactionImpl: BdkPartiallySignedTransaction {

    let ffiPsbt: PartiallySignedTransaction

    public init(ffiPsbt: PartiallySignedTransaction) {
        self.ffiPsbt = ffiPsbt
    }

    public func feeAmount() -> KotlinULong? {
        return ffiPsbt.feeAmount().flatMap { KotlinULong(value: $0) }
    }

    public func serialize() -> String {
        return ffiPsbt.serialize()
    }

    public func txid() -> String {
        return ffiPsbt.txid()
    }

    public func extractTx() -> BdkTransaction {
        return BdkTransactionImpl(ffiTransaction: ffiPsbt.extractTx())
    }

}
