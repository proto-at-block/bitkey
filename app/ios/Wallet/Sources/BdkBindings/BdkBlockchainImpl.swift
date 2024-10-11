import BitcoinDevKit
import Shared

class BdkBlockchainImpl: BdkBlockchain {

    let ffiBlockchain: Blockchain

    init(ffiBlockchain: Blockchain) {
        self.ffiBlockchain = ffiBlockchain
    }

    func broadcastBlocking(transaction: BdkTransaction) -> BdkResult<KotlinUnit> {
        return BdkResult {
            let realTransaction = transaction as! BdkTransactionImpl
            try ffiBlockchain.broadcast(transaction: realTransaction.ffiTransaction)
            return KotlinUnit()
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

    func getTx(txid: String) -> BdkResult<any BdkTransaction> {
        return BdkResult<BdkTransaction> {
            switch try ffiBlockchain.getTx(txid: txid) {
            case .none:
                throw BdkError
                    .TransactionNotFound(message: "Transaction with id \(txid) not found.")
            case let .some(tx):
                return tx.toBdkTransaction()
            }
        }
    }

}
