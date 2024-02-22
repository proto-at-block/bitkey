import BitcoinDevKit
import Shared

extension Shared.BdkError {
    
    /**
     * Maps `BitcoinDevKit.BdkError` type from `bdk-swift` to KMP `Shared.BdkError` type.
     */
    static func create(_ error: Error) -> Shared.BdkError {
        let bdkError = error as! BitcoinDevKit.BdkError
        // Convert Swift Error to Kotlin Throwable.
        let throwable = NSErrorKt.asThrowable(error)
        
        switch bdkError {
        case .InvalidU32Bytes(let message):
            return InvalidU32Bytes(cause: throwable, message: message)
        case .Generic(let message):
            return Generic(cause: throwable, message: message)
        case .MissingCachedScripts(let message):
            return MissingCachedScripts(cause: throwable, message: message)
        case .ScriptDoesntHaveAddressForm(let message):
            return ScriptDoesntHaveAddressForm(cause: throwable, message: message)
        case .NoRecipients(let message):
            return NoRecipients(cause: throwable, message: message)
        case .NoUtxosSelected(let message):
            return NoUtxosSelected(cause: throwable, message: message)
        case .OutputBelowDustLimit(let message):
            return OutputBelowDustLimit(cause: throwable, message: message)
        case .InsufficientFunds(let message):
            return InsufficientFunds(cause: throwable, message: message)
        case .BnBTotalTriesExceeded(let message):
            return BnBTotalTriesExceeded(cause: throwable, message: message)
        case .BnBNoExactMatch(let message):
            return BnBNoExactMatch(cause: throwable, message: message)
        case .UnknownUtxo(let message):
            return UnknownUtxo(cause: throwable, message: message)
        case .TransactionNotFound(let message):
            return TransactionNotFound(cause: throwable, message: message)
        case .TransactionConfirmed(let message):
            return TransactionConfirmed(cause: throwable, message: message)
        case .IrreplaceableTransaction(let message):
            return IrreplaceableTransaction(cause: throwable, message: message)
        case .FeeRateTooLow(let message):
            return FeeRateTooLow(cause: throwable, message: message)
        case .FeeTooLow(let message):
            return FeeTooLow(cause: throwable, message: message)
        case .FeeRateUnavailable(let message):
            return FeeRateUnavailable(cause: throwable, message: message)
        case .MissingKeyOrigin(let message):
            return MissingKeyOrigin(cause: throwable, message: message)
        case .Key(let message):
            return Key(cause: throwable, message: message)
        case .ChecksumMismatch(let message):
            return ChecksumMismatch(cause: throwable, message: message)
        case .SpendingPolicyRequired(let message):
            return SpendingPolicyRequired(cause: throwable, message: message)
        case .InvalidPolicyPathError(let message):
            return InvalidPolicyPathException(cause: throwable, message: message)
        case .Signer(let message):
            return Signer(cause: throwable, message: message)
        case .InvalidNetwork(let message):
            return InvalidNetwork(cause: throwable, message: message)
        case .InvalidProgressValue(let message):
            return InvalidProgressValue(cause: throwable, message: message)
        case .ProgressUpdateError(let message):
            return ProgressUpdateException(cause: throwable, message: message)
        case .InvalidOutpoint(let message):
            return InvalidOutpoint(cause: throwable, message: message)
        case .Descriptor(let message):
            return Descriptor(cause: throwable, message: message)
        case .Encode(let message):
            return Encode(cause: throwable, message: message)
        case .Miniscript(let message):
            return Miniscript(cause: throwable, message: message)
        case .MiniscriptPsbt(let message):
            return MiniscriptPsbt(cause: throwable, message: message)
        case .Bip32(let message):
            return Bip32(cause: throwable, message: message)
        case .Secp256k1(let message):
            return Secp256k1(cause: throwable, message: message)
        case .Json(let message):
            return Json(cause: throwable, message: message)
        case .Hex(let message):
            return Hex(cause: throwable, message: message)
        case .Psbt(let message):
            return Psbt(cause: throwable, message: message)
        case .PsbtParse(let message):
            return PsbtParse(cause: throwable, message: message)
        case .Electrum(let message):
            return Electrum(cause: throwable, message: message)
        case .Esplora(let message):
            return Esplora(cause: throwable, message: message)
        case .Sled(let message):
            return Sled(cause: throwable, message: message)
        case .Rusqlite(let message):
            return Rusqlite(cause: throwable, message: message)
        case .Rpc(message: let message):
            return Rpc(cause: throwable, message: message)
        case .HardenedIndex(let message):
            return HardenedIndex(cause: throwable, message: message)
        }
    }
}
