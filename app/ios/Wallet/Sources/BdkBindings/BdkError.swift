import BitcoinDevKitLegacy
import Shared

extension Shared.BdkError {

    /**
     * Maps `BitcoinDevKitLegacy.BdkError` type from `bdk-swift` to KMP `Shared.BdkError` type.
     */
    static func create(_ error: Error) -> Shared.BdkError {
        let bdkError = error as! BitcoinDevKitLegacy.BdkError
        // Convert Swift Error to Kotlin Throwable.
        let throwable = NSErrorKt.asThrowable(error)

        switch bdkError {
        case let .InvalidU32Bytes(message):
            return InvalidU32Bytes(cause: throwable, message: message)
        case let .Generic(message):
            return Generic(cause: throwable, message: message)
        case let .MissingCachedScripts(message):
            return MissingCachedScripts(cause: throwable, message: message)
        case let .ScriptDoesntHaveAddressForm(message):
            return ScriptDoesntHaveAddressForm(cause: throwable, message: message)
        case let .NoRecipients(message):
            return NoRecipients(cause: throwable, message: message)
        case let .NoUtxosSelected(message):
            return NoUtxosSelected(cause: throwable, message: message)
        case let .OutputBelowDustLimit(message):
            return OutputBelowDustLimit(cause: throwable, message: message)
        case let .InsufficientFunds(message):
            // replace message with our own to avoid leaking sensitive information
            // don't pass the cause for the same reason
            return InsufficientFunds(cause: nil, message: "insufficient funds to create tx")
        case let .BnBTotalTriesExceeded(message):
            return BnBTotalTriesExceeded(cause: throwable, message: message)
        case let .BnBNoExactMatch(message):
            return BnBNoExactMatch(cause: throwable, message: message)
        case let .UnknownUtxo(message):
            return UnknownUtxo(cause: throwable, message: message)
        case let .TransactionNotFound(message):
            return TransactionNotFound(cause: throwable, message: message)
        case let .TransactionConfirmed(message):
            return TransactionConfirmed(cause: throwable, message: message)
        case let .IrreplaceableTransaction(message):
            return IrreplaceableTransaction(cause: throwable, message: message)
        case let .FeeRateTooLow(message):
            return FeeRateTooLow(cause: throwable, message: message)
        case let .FeeTooLow(message):
            return FeeTooLow(cause: throwable, message: message)
        case let .FeeRateUnavailable(message):
            return FeeRateUnavailable(cause: throwable, message: message)
        case let .MissingKeyOrigin(message):
            return MissingKeyOrigin(cause: throwable, message: message)
        case let .Key(message):
            return Key(cause: throwable, message: message)
        case let .ChecksumMismatch(message):
            return ChecksumMismatch(cause: throwable, message: message)
        case let .SpendingPolicyRequired(message):
            return SpendingPolicyRequired(cause: throwable, message: message)
        case let .InvalidPolicyPathError(message):
            return InvalidPolicyPathException(cause: throwable, message: message)
        case let .Signer(message):
            return Signer(cause: throwable, message: message)
        case let .InvalidNetwork(message):
            return InvalidNetwork(cause: throwable, message: message)
        case let .InvalidProgressValue(message):
            return InvalidProgressValue(cause: throwable, message: message)
        case let .ProgressUpdateError(message):
            return ProgressUpdateException(cause: throwable, message: message)
        case let .InvalidOutpoint(message):
            return InvalidOutpoint(cause: throwable, message: message)
        case let .Descriptor(message):
            return Descriptor(cause: throwable, message: message)
        case let .Encode(message):
            return Encode(cause: throwable, message: message)
        case let .Miniscript(message):
            return Miniscript(cause: throwable, message: message)
        case let .MiniscriptPsbt(message):
            return MiniscriptPsbt(cause: throwable, message: message)
        case let .Bip32(message):
            return Bip32(cause: throwable, message: message)
        case let .Secp256k1(message):
            return Secp256k1(cause: throwable, message: message)
        case let .Json(message):
            return Json(cause: throwable, message: message)
        case let .Hex(message):
            return Hex(cause: throwable, message: message)
        case let .Psbt(message):
            return Psbt(cause: throwable, message: message)
        case let .PsbtParse(message):
            return PsbtParse(cause: throwable, message: message)
        case let .Electrum(message):
            return Electrum(cause: throwable, message: message)
        case let .Esplora(message):
            return Esplora(cause: throwable, message: message)
        case let .Sled(message):
            return Sled(cause: throwable, message: message)
        case let .Rusqlite(message):
            return Rusqlite(cause: throwable, message: message)
        case let .Rpc(message: message):
            return Rpc(cause: throwable, message: message)
        case let .HardenedIndex(message):
            return HardenedIndex(cause: throwable, message: message)
        }
    }
}
