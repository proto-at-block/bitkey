package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.catchingResult
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import org.bitcoindevkit.BdkException
import org.bitcoindevkit.BdkException.*

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation
 * was successful, catching any [BdkException] that was thrown from the [block] function
 * execution and encapsulating it as a [BdkError] failure.
 */
internal inline fun <V : Any?> runCatchingBdkError(block: () -> V): BdkResult<V> =
  catchingResult { block() }
    .mapError {
      require(it is BdkException)
      it.toBdkError()
    }
    .mapBoth(
      success = { value -> BdkResult.Ok(value) },
      failure = { error -> BdkResult.Err(error) }
    )

/**
 * Maps [BdkException] from `bdk-android` to KMP [BdkError] type.
 */
internal fun BdkException.toBdkError(): BdkError {
  val cause = this
  // Bdk error messages are always present.
  val message = requireNotNull(message)

  return when (this) {
    is Bip32 -> BdkError.Bip32(cause, message)
    is BnBNoExactMatch -> BdkError.BnBNoExactMatch(cause, message)
    is BnBTotalTriesExceeded -> BdkError.BnBTotalTriesExceeded(cause, message)
    is ChecksumMismatch -> BdkError.ChecksumMismatch(cause, message)
    is Descriptor -> BdkError.Descriptor(cause, message)
    is Electrum -> BdkError.Electrum(cause, message)
    is Encode -> BdkError.Encode(cause, message)
    is Esplora -> BdkError.Esplora(cause, message)
    is FeeRateTooLow -> BdkError.FeeRateTooLow(cause, message)
    is FeeRateUnavailable -> BdkError.FeeRateUnavailable(cause, message)
    is FeeTooLow -> BdkError.FeeTooLow(cause, message)
    is Generic -> BdkError.Generic(cause, message)
    is HardenedIndex -> BdkError.HardenedIndex(cause, message)
    is Hex -> BdkError.Hex(cause, message)
    // replace message with our own to avoid leaking sensitive information
    // don't pass the cause for the same reason
    is InsufficientFunds -> BdkError.InsufficientFunds(null, "insufficient funds to create tx")
    is InvalidNetwork -> BdkError.InvalidNetwork(cause, message)
    is InvalidOutpoint -> BdkError.InvalidOutpoint(cause, message)
    is InvalidPolicyPathException -> BdkError.InvalidPolicyPathException(cause, message)
    is InvalidProgressValue -> BdkError.InvalidProgressValue(cause, message)
    is InvalidU32Bytes -> BdkError.InvalidU32Bytes(cause, message)
    is IrreplaceableTransaction -> BdkError.IrreplaceableTransaction(cause, message)
    is Json -> BdkError.Json(cause, message)
    is Key -> BdkError.Key(cause, message)
    is Miniscript -> BdkError.Miniscript(cause, message)
    is MiniscriptPsbt -> BdkError.MiniscriptPsbt(cause, message)
    is MissingCachedScripts -> BdkError.MissingCachedScripts(cause, message)
    is MissingKeyOrigin -> BdkError.MissingKeyOrigin(cause, message)
    is NoRecipients -> BdkError.NoRecipients(cause, message)
    is NoUtxosSelected -> BdkError.NoUtxosSelected(cause, message)
    is OutputBelowDustLimit -> BdkError.OutputBelowDustLimit(cause, message)
    is ProgressUpdateException -> BdkError.ProgressUpdateException(cause, message)
    is Psbt -> BdkError.Psbt(cause, message)
    is PsbtParse -> BdkError.PsbtParse(cause, message)
    is Rpc -> BdkError.Rpc(cause, message)
    is Rusqlite -> BdkError.Rusqlite(cause, message)
    is ScriptDoesntHaveAddressForm -> BdkError.ScriptDoesntHaveAddressForm(cause, message)
    is Secp256k1 -> BdkError.Secp256k1(cause, message)
    is Signer -> BdkError.Signer(cause, message)
    is Sled -> BdkError.Sled(cause, message)
    is SpendingPolicyRequired -> BdkError.SpendingPolicyRequired(cause, message)
    is TransactionConfirmed -> BdkError.TransactionConfirmed(cause, message)
    is TransactionNotFound -> BdkError.TransactionNotFound(cause, message)
    is UnknownUtxo -> BdkError.UnknownUtxo(cause, message)
  }
}
