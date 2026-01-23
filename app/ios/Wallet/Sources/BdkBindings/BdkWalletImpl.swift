import BitcoinDevKitLegacy
import Shared

class BdkWalletImpl: BdkWallet {
    let wallet: Wallet

    init(wallet: Wallet) {
        self.wallet = wallet
    }

    func getAddressBlocking(addressIndex: BdkAddressIndex) -> BdkResult<BdkAddressInfo> {
        let ffiAddressIndex: AddressIndex
        switch addressIndex {
        case is BdkAddressIndex.New:
            ffiAddressIndex = AddressIndex.new
        case is BdkAddressIndex.LastUnused:
            ffiAddressIndex = AddressIndex.lastUnused
        case let peek as BdkAddressIndex.Peek:
            ffiAddressIndex = AddressIndex.peek(index: peek.index)
        default:
            fatalError("Unknown BdkAddressIndex type: \(addressIndex)")
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
            return try KotlinBoolean(bool: wallet.sign(psbt: realBdkPsbt.ffiPsbt, signOptions: nil))
        }
    }

    func syncBlocking(blockchain: BdkBlockchain, progress: BdkProgress?) -> BdkResult<KotlinUnit> {
        guard let realBdkBlockchain = blockchain as? LegacyBdkBlockchainImpl else {
            return Shared.BdkResultErr(
                error: Shared.BdkError.Generic(
                    cause: nil,
                    message: "BdkWalletImpl sync operations require LegacyBdkBlockchainImpl because wallet sync specifically depends on the legacy blockchain implementation. Got \(type(of: blockchain))."
                )
            )
        }
        let ffiProgress = progress.map { Progress(progress: $0) }
        return BdkResult {
            try wallet.sync(blockchain: realBdkBlockchain.ffiBlockchain, progress: ffiProgress)
            return KotlinUnit()
        }
    }

    func isMineBlocking(script: BdkScript) -> BdkResult<KotlinBoolean> {
        let realBdkScript = script as! BdkScriptImpl
        return BdkResult {
            try .init(bool: wallet.isMine(script: realBdkScript.toFfiScript()))
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
