import BitcoinDevKit
import Shared

class BdkWalletImpl : BdkWallet {
    let wallet: Wallet
    
    init(wallet: Wallet) {
        self.wallet = wallet
    }
    
    func getAddressBlocking(addressIndex: BdkAddressIndex) -> BdkResult<BdkAddressInfo> {
        var ffiAddressIndex: AddressIndex
        switch addressIndex {
        case .theNew:
            ffiAddressIndex = AddressIndex.new
        case .lastUnused:
            ffiAddressIndex = AddressIndex.lastUnused
        default:
            fatalError()
        }
        return BdkResult {
            let ffiAddressInfo = try wallet.getAddress(addressIndex: ffiAddressIndex)
            return BdkAddressInfo(
                index: Int64(ffiAddressInfo.index),
                address: BdkAddressImpl(address: ffiAddressInfo.address)
            )
        }
    }
    
    func getBalanceBlocking() -> BdkResult<BdkBalance> {
        return BdkResult {
            let ffiBalance = try wallet.getBalance()
            return BdkBalance(
                immature: ffiBalance.immature,
                trustedPending: ffiBalance.trustedPending,
                untrustedPending: ffiBalance.untrustedPending,
                confirmed: ffiBalance.confirmed,
                spendable: ffiBalance.spendable,
                total_: ffiBalance.total
            )
        }
    }
    
    func listTransactionsBlocking(includeRaw: Bool) -> BdkResult<NSArray> {
        return BdkResult {
            return try wallet.listTransactions(includeRaw: includeRaw).map { tx in
                BdkTransactionDetails(
                    transaction: tx.transaction.map { BdkTransactionImpl(ffiTransaction: $0) },
                    fee: tx.fee.map { fee in KotlinULong(value: fee) },
                    received: tx.received,
                    sent: tx.sent,
                    txid: tx.txid,
                    confirmationTime_: tx.confirmationTime.map { blockTime in
                        BdkBlockTime(
                            height: Int64(blockTime.height),
                            timestampEpochSeconds: Int64(blockTime.timestamp)
                        )
                    }
                )
            } as NSArray
        }
    }
    
    func signBlocking(psbt: BdkPartiallySignedTransaction) -> BdkResult<KotlinBoolean> {
        let realBdkPsbt = psbt as! BdkPartiallySignedTransactionImpl
        return BdkResult {
            return KotlinBoolean(bool: try wallet.sign(psbt: realBdkPsbt.ffiPsbt, signOptions: nil))
        }
    }
    
    func syncBlocking(blockchain: BdkBlockchain, progress: BdkProgress?) -> BdkResult<KotlinUnit> {
        let realBdkBlockchain = blockchain as! BdkBlockchainImpl
        let ffiProgress = progress.map { Progress(progress: $0) }
        return BdkResult {
            try wallet.sync(blockchain: realBdkBlockchain.ffiBlockchain, progress: ffiProgress)
            return KotlinUnit()
        }
    }

    func isMineBlocking(script: BdkScript) -> BdkResult<KotlinBoolean> {
        let realBdkScript = script as! BdkScriptImpl
        return BdkResult {
            .init(bool: (try wallet.isMine(script: realBdkScript.ffiScript)))
        }
    }
    
    func listUnspentBlocking() -> BdkResult<NSArray> {
        return BdkResult {
            return try wallet.listUnspent().map { utxo in
                BdkUtxo(
                    outPoint: utxo.outpoint.toBdkOutPoint(),
                    txOut: utxo.txout.toBdkTxOut(),
                    isSpent: utxo.isSpent
                )
            } as NSArray
        }
    }
}

private class Progress: FfiProgress {
    
    private let progress: BdkProgress
    
    init(progress: BdkProgress) {
        self.progress = progress
    }
    
    func update(progress: Float, message: String?) {
        self.progress.update(progress: progress, message: message)
    }
}
