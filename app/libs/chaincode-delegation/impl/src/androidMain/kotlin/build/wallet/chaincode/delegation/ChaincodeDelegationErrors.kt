package build.wallet.chaincode.delegation

import build.wallet.catchingResult
import build.wallet.rust.core.ChaincodeDelegationException
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [ChaincodeDelegationException] that was
 * thrown from the [block] function execution and encapsulating it as a [ChaincodeDelegationError] failure.
 */
internal inline infix fun <T, V : Any> T.runCatchingChaincodeDelegationError(
  block: T.() -> V,
): ChaincodeDelegationResult<V> =
  catchingResult { block() }
    .mapError {
      (it as? ChaincodeDelegationException)?.toChaincodeDelegationError() ?: ChaincodeDelegationError.Unknown(it, it.message)
    }
    .let {
      it.mapBoth(
        success = { value -> ChaincodeDelegationResult.Ok(value) },
        failure = { error -> ChaincodeDelegationResult.Err(error) }
      )
    }

/**
 * Maps [ChaincodeDelegationException] from `core` to KMP [ChaincodeDelegationError] type.
 */
internal fun ChaincodeDelegationException.toChaincodeDelegationError(): ChaincodeDelegationError {
  val cause = this
  val message = requireNotNull(message)

  return when (this) {
    is ChaincodeDelegationException.KeyDerivation -> ChaincodeDelegationError.KeyDerivation(cause, message)
    is ChaincodeDelegationException.KeyMismatch -> ChaincodeDelegationError.KeyMismatch(cause, message)
    is ChaincodeDelegationException.TweakComputation -> ChaincodeDelegationError.TweakComputation(cause, message)
    is ChaincodeDelegationException.UnknownKey -> ChaincodeDelegationError.UnknownKey(cause, message)
    is ChaincodeDelegationException.InvalidPsbt -> ChaincodeDelegationError.InvalidPsbt(cause, message)
  }
}
