package build.wallet.frost

import build.wallet.catchingResult
import build.wallet.rust.core.SigningException
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [SigningException] that was
 * thrown from the [block] function execution and encapsulating it as a [SigningError] failure.
 */
internal inline infix fun <T, V : Any> T.runCatchingSigningError(
  block: T.() -> V,
): SigningResult<V> =
  catchingResult { block() }
    .mapError {
      require(it is SigningException)
      it.toSigningError()
    }
    .let {
      it.mapBoth(
        success = { value -> SigningResult.Ok(value) },
        failure = { error -> SigningResult.Err(error) }
      )
    }

/**
 * Maps [SigningError] from `core` to KMP [SigningError] type.
 */
internal fun SigningException.toSigningError(): SigningError {
  val cause = this
  // Signing error messages are always present.
  val message = requireNotNull(message)

  return when (this) {
    is SigningException.CommitmentMismatch -> build.wallet.frost.SigningError.CommitmentMismatch(cause, message)
    is SigningException.InvalidCounterpartyCommitments -> build.wallet.frost.SigningError.InvalidCounterpartyCommitments(cause, message)
    is SigningException.InvalidPsbt -> build.wallet.frost.SigningError.InvalidPsbt(cause, message)
    is SigningException.MissingCounterpartyNonces -> build.wallet.frost.SigningError.MissingCounterpartyNonces(cause, message)
    is SigningException.NonceAlreadyUsed -> build.wallet.frost.SigningError.NonceAlreadyUsed(cause, message)
    is SigningException.UnableToRetrieveSighash -> build.wallet.frost.SigningError.UnableToRetrieveSighash(cause, message)
    is SigningException.UnableToFinalizePsbt -> build.wallet.frost.SigningError.UnableToFinalizePsbt(cause, message)
  }
}
