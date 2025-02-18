package build.wallet.frost

import build.wallet.catchingResult
import build.wallet.rust.core.KeygenException
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [KeygenException] that was
 * thrown from the [block] function execution and encapsulating it as a [KeygenError] failure.
 */
inline infix fun <T, V : Any> T.runCatchingKeygenError(block: T.() -> V): KeygenResult<V> =
  catchingResult { block() }
    .mapError {
      require(it is KeygenException)
      it.toKeygenError()
    }
    .let {
      it.mapBoth(
        success = { value -> KeygenResult.Ok(value) },
        failure = { error -> KeygenResult.Err(error) }
      )
    }

/**
 * Maps [KeygenException] from `core` to KMP [KeygenError] type.
 */
fun KeygenException.toKeygenError(): KeygenError {
  val cause = this
  // Keygen error messages are always present.
  val message = requireNotNull(message)

  return when (this) {
    is KeygenException.MissingSharePackage -> KeygenError.MissingSharePackage(cause, message)
    is KeygenException.InvalidProofOfKnowledge -> KeygenError.InvalidProofOfKnowledge(cause, message)
    is KeygenException.InvalidIntermediateShare -> KeygenError.InvalidIntermediateShare(cause, message)
    is KeygenException.InvalidKeyCommitments -> KeygenError.InvalidKeyCommitments(cause, message)
    is KeygenException.InvalidParticipants -> KeygenError.InvalidParticipants(cause, message)
    is KeygenException.ShareAggregationFailed -> KeygenError.ShareAggregationFailed(cause, message)
    is KeygenException.VerificationShareGenerationFailed -> KeygenError.VerificationShareGenerationFailed(cause, message)
  }
}
