package build.wallet.nfc.transaction

/**
 * Sealed class representing errors thrown during NFC transaction operations.
 *
 * @property message Internal description of the error.
 */
sealed class TransactionError(
  message: String,
) : Error(message) {
  /**
   * Error indicating that a transaction could not be signed because hardware
   * verification is enabled but was not provided.
   */
  class VerificationRequired : TransactionError("Transaction could not be signed without verification.")
}
