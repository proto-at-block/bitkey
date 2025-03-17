package build.wallet.bdk.bindings

/**
 * Represents errors which can be thrown by BDK bindings:
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L6
 */
sealed class BdkError(
  override val cause: Throwable?,
  override val message: String?,
) : Error(message, cause) {
  class Bip32(cause: Throwable?, message: String?) : BdkError(cause, message)

  class BnBNoExactMatch(cause: Throwable?, message: String?) : BdkError(cause, message)

  class BnBTotalTriesExceeded(cause: Throwable?, message: String?) : BdkError(cause, message)

  class ChecksumMismatch(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Descriptor(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Electrum(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Encode(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Esplora(cause: Throwable?, message: String?) : BdkError(cause, message)

  class FeeRateTooLow(cause: Throwable?, message: String?) : BdkError(cause, message)

  class FeeRateUnavailable(cause: Throwable?, message: String?) : BdkError(cause, message)

  class FeeTooLow(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Generic(cause: Throwable?, message: String?) : BdkError(cause, message)

  class HardenedIndex(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Hex(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InsufficientFunds(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InvalidNetwork(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InvalidOutpoint(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InvalidPolicyPathException(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InvalidProgressValue(cause: Throwable?, message: String?) : BdkError(cause, message)

  class InvalidU32Bytes(cause: Throwable?, message: String?) : BdkError(cause, message)

  class IrreplaceableTransaction(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Json(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Key(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Miniscript(cause: Throwable?, message: String?) : BdkError(cause, message)

  class MiniscriptPsbt(cause: Throwable?, message: String?) : BdkError(cause, message)

  class MissingCachedScripts(cause: Throwable?, message: String?) : BdkError(cause, message)

  class MissingKeyOrigin(cause: Throwable?, message: String?) : BdkError(cause, message)

  class NoRecipients(cause: Throwable?, message: String?) : BdkError(cause, message)

  class NoUtxosSelected(cause: Throwable?, message: String?) : BdkError(cause, message)

  class OutputBelowDustLimit(cause: Throwable?, message: String?) : BdkError(cause, message)

  class ProgressUpdateException(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Psbt(cause: Throwable?, message: String?) : BdkError(cause, message)

  class PsbtParse(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Rpc(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Rusqlite(cause: Throwable?, message: String?) : BdkError(cause, message)

  class ScriptDoesntHaveAddressForm(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Secp256k1(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Signer(cause: Throwable?, message: String?) : BdkError(cause, message)

  class Sled(cause: Throwable?, message: String?) : BdkError(cause, message)

  class SpendingPolicyRequired(cause: Throwable?, message: String?) : BdkError(cause, message)

  class TransactionConfirmed(cause: Throwable?, message: String?) : BdkError(cause, message)

  class TransactionNotFound(cause: Throwable?, message: String?) : BdkError(cause, message)

  class UnknownUtxo(cause: Throwable?, message: String?) : BdkError(cause, message)

  /**
   * Convert to Name-Only string.
   *
   * BDK Errors can contain sensitive data. When converting this to
   * a string, we redact all but the underlying error type.
   */
  override fun toString(): String = "BdkError(${this::class.simpleName})"
}
