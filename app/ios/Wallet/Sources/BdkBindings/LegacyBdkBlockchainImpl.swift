import BitcoinDevKitLegacy
import Shared

/// Legacy BDK implementation of BdkBlockchain using BitcoinDevKitLegacy.
/// Used when Bdk2FeatureFlag is disabled.
class LegacyBdkBlockchainImpl: BdkBlockchain {

    let ffiBlockchain: Blockchain

    init(ffiBlockchain: Blockchain) {
        self.ffiBlockchain = ffiBlockchain
    }

    func broadcastBlocking(transaction: BdkTransaction) -> BdkResult<NSString> {
        return BdkResult {
            let realTransaction = transaction as! BdkTransactionImpl
            try ffiBlockchain.broadcast(transaction: realTransaction.ffiTransaction)
            return realTransaction.ffiTransaction.txid() as NSString
        }
    }

    func getBlockHashBlocking(height: Int64) -> BdkResult<NSString> {
        return BdkResult {
            try ffiBlockchain.getBlockHash(height: UInt32(height)) as NSString
        }
    }

    func getHeightBlocking() -> BdkResult<KotlinLong> {
        return BdkResult {
            try KotlinLong(value: Int64(ffiBlockchain.getHeight()))
        }
    }

    func estimateFeeBlocking(targetBlocks: UInt64) -> BdkResult<KotlinFloat> {
        return BdkResult {
            try KotlinFloat(value: ffiBlockchain.estimateFee(target: targetBlocks).asSatPerVb())
        }
    }

    func getTxBlocking(txid: String) -> BdkResult<BdkTransaction> {
        return BdkResult {
            guard let tx = try ffiBlockchain.getTx(txid: txid) else {
                throw BdkError
                    .TransactionNotFound(message: "Transaction with id \(txid) not found.")
            }
            return tx.toBdkTransaction()
        }
    }

}
