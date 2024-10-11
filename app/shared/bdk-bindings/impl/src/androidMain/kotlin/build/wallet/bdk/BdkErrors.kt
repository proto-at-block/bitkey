package build.wallet.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.catchingResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import org.bitcoindevkit.BdkException
import org.bitcoindevkit.BdkException.Bip32
import org.bitcoindevkit.BdkException.BnBNoExactMatch
import org.bitcoindevkit.BdkException.BnBTotalTriesExceeded
import org.bitcoindevkit.BdkException.ChecksumMismatch
import org.bitcoindevkit.BdkException.Descriptor
import org.bitcoindevkit.BdkException.Electrum
import org.bitcoindevkit.BdkException.Encode
import org.bitcoindevkit.BdkException.Esplora
import org.bitcoindevkit.BdkException.FeeRateTooLow
import org.bitcoindevkit.BdkException.FeeRateUnavailable
import org.bitcoindevkit.BdkException.FeeTooLow
import org.bitcoindevkit.BdkException.Generic
import org.bitcoindevkit.BdkException.HardenedIndex
import org.bitcoindevkit.BdkException.Hex
import org.bitcoindevkit.BdkException.InsufficientFunds
import org.bitcoindevkit.BdkException.InvalidNetwork
import org.bitcoindevkit.BdkException.InvalidOutpoint
import org.bitcoindevkit.BdkException.InvalidPolicyPathException
import org.bitcoindevkit.BdkException.InvalidProgressValue
import org.bitcoindevkit.BdkException.InvalidU32Bytes
import org.bitcoindevkit.BdkException.IrreplaceableTransaction
import org.bitcoindevkit.BdkException.Json
import org.bitcoindevkit.BdkException.Key
import org.bitcoindevkit.BdkException.Miniscript
import org.bitcoindevkit.BdkException.MiniscriptPsbt
import org.bitcoindevkit.BdkException.MissingCachedScripts
import org.bitcoindevkit.BdkException.MissingKeyOrigin
import org.bitcoindevkit.BdkException.NoRecipients
import org.bitcoindevkit.BdkException.NoUtxosSelected
import org.bitcoindevkit.BdkException.OutputBelowDustLimit
import org.bitcoindevkit.BdkException.ProgressUpdateException
import org.bitcoindevkit.BdkException.Psbt
import org.bitcoindevkit.BdkException.PsbtParse
import org.bitcoindevkit.BdkException.Rpc
import org.bitcoindevkit.BdkException.Rusqlite
import org.bitcoindevkit.BdkException.ScriptDoesntHaveAddressForm
import org.bitcoindevkit.BdkException.Secp256k1
import org.bitcoindevkit.BdkException.Signer
import org.bitcoindevkit.BdkException.Sled
import org.bitcoindevkit.BdkException.SpendingPolicyRequired
import org.bitcoindevkit.BdkException.TransactionConfirmed
import org.bitcoindevkit.BdkException.TransactionNotFound
import org.bitcoindevkit.BdkException.UnknownUtxo

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [BdkException] that was
 * thrown from the [block] function execution and encapsulating it as a [BdkError] failure.
 */
internal inline infix fun <T, V : Any?> T.runCatchingBdkError(block: T.() -> V): BdkResult<V> =
  catchingResult { block() }
    .mapError {
      require(it is BdkException)
      it.toBdkError()
    }
    .let {
      it.mapBoth(
        success = { value -> BdkResult.Ok(value) },
        failure = { error -> BdkResult.Err(error) }
      )
    }

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
    is InsufficientFunds -> BdkError.InsufficientFunds(cause, message)
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
