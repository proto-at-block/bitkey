package build.wallet.ldk.bindings

sealed class LdkNodeError(override val message: String) : Exception(message) {
  class AlreadyRunning(message: String) : LdkNodeError(message)

  class NotRunning(message: String) : LdkNodeError(message)

  class OnchainTxCreationFailed(message: String) : LdkNodeError(message)

  class ConnectionFailed(message: String) : LdkNodeError(message)

  class AddressInvalid(message: String) : LdkNodeError(message)

  class PublicKeyInvalid(message: String) : LdkNodeError(message)

  class PaymentHashInvalid(message: String) : LdkNodeError(message)

  class PaymentPreimageInvalid(message: String) : LdkNodeError(message)

  class PaymentSecretInvalid(message: String) : LdkNodeError(message)

  class NonUniquePaymentHash(message: String) : LdkNodeError(message)

  class InvalidAmount(message: String) : LdkNodeError(message)

  class InvalidInvoice(message: String) : LdkNodeError(message)

  class InvoiceCreationFailed(message: String) : LdkNodeError(message)

  class InsufficientFunds(message: String) : LdkNodeError(message)

  class PaymentFailed(message: String) : LdkNodeError(message)

  class ChannelIdInvalid(message: String) : LdkNodeError(message)

  class NetworkInvalid(message: String) : LdkNodeError(message)

  class PeerInfoParseFailed(message: String) : LdkNodeError(message)

  class ChannelCreationFailed(message: String) : LdkNodeError(message)

  class ChannelClosingFailed(message: String) : LdkNodeError(message)

  class PersistenceFailed(message: String) : LdkNodeError(message)

  class WalletOperationFailed(message: String) : LdkNodeError(message)

  class WalletSigningFailed(message: String) : LdkNodeError(message)

  class TxSyncFailed(message: String) : LdkNodeError(message)
}
