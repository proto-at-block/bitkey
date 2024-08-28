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
internal inline infix fun <T, V : Any> T.runCatchingKeygenError(block: T.() -> V): KeygenResult<V> =
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
internal fun KeygenException.toKeygenError(): KeygenError {
  val cause = this
  // Keygen error messages are always present.
  val message = requireNotNull(message)

  return when (this) {
    is KeygenException.InvalidIntermediateShare -> KeygenError.InvalidIntermediateShare(cause, message)
    is KeygenException.InvalidKeyCommitments -> KeygenError.InvalidKeyCommitments(cause, message)
    is KeygenException.InvalidParticipantIndex -> KeygenError.InvalidParticipantIndex(cause, message)
    is KeygenException.InvalidProofOfKnowledge -> KeygenError.InvalidProofOfKnowledge(cause, message)
    is KeygenException.MissingShareAggParams -> KeygenError.MissingShareAggParams(cause, message)
    is KeygenException.MissingSharePackage -> KeygenError.MissingSharePackage(cause, message)
  }
}
