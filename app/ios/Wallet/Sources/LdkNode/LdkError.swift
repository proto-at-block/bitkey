import LightningDevKitNode
import Shared

extension Shared.LdkNodeError {
    /**
     * Maps `LightningDevKitNode.LdkError` type from `ldk-node` to KMP `Shared.LdkError` type.
     */
    
    static func create(_ ldkNodeError: LightningDevKitNode.NodeError) -> Shared.LdkNodeError {
        switch ldkNodeError {
        case .AlreadyRunning(let message):
            return AlreadyRunning(message: message)
        case .NotRunning(let message):
            return NotRunning(message: message)
        case .OnchainTxCreationFailed(let message):
            return OnchainTxCreationFailed(message: message)
        case .ConnectionFailed(let message):
            return ConnectionFailed(message: message)
        case .AddressInvalid(let message):
            return AddressInvalid(message: message)
        case .PublicKeyInvalid(let message):
            return PublicKeyInvalid(message: message)
        case .PaymentHashInvalid(let message):
            return PaymentHashInvalid(message: message)
        case .PaymentPreimageInvalid(let message):
            return PaymentPreimageInvalid(message: message)
        case .PaymentSecretInvalid(let message):
            return PaymentSecretInvalid(message: message)
        case .NonUniquePaymentHash(let message):
            return NonUniquePaymentHash(message: message)
        case .InvalidAmount(let message):
            return InvalidAmount(message: message)
        case .InvalidInvoice(let message):
            return InvalidInvoice(message: message)
        case .InvoiceCreationFailed(let message):
            return InvoiceCreationFailed(message: message)
        case .InsufficientFunds(let message):
            return InsufficientFunds(message: message)
        case .PaymentFailed(let message):
            return PaymentFailed(message: message)
        case .ChannelIdInvalid(let message):
            return ChannelIdInvalid(message: message)
        case .NetworkInvalid(let message):
            return NetworkInvalid(message: message)
        case .PeerInfoParseFailed(let message):
            return PeerInfoParseFailed(message: message)
        case .ChannelCreationFailed(let message):
            return ChannelCreationFailed(message: message)
        case .ChannelClosingFailed(let message):
            return ChannelClosingFailed(message: message)
        case .PersistenceFailed(let message):
            return PersistenceFailed(message: message)
        case .WalletOperationFailed(let message):
            return WalletOperationFailed(message: message)
        case .WalletSigningFailed(let message):
            return WalletSigningFailed(message: message)
        case .TxSyncFailed(let message):
            return TxSyncFailed(message: message)
        }
    }
}
