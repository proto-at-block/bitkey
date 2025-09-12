package build.wallet.chaincode.delegation

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [ChaincodeDelegationError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class ChaincodeDelegationResult<out V : Any> {
  abstract val result: Result<V, ChaincodeDelegationError>

  /**
   * Wraps [ResultOk] with [ChaincodeDelegationError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : ChaincodeDelegationResult<V>() {
    override val result: Result<V, ChaincodeDelegationError> = ResultOk(value)
  }

  /**
   * Wraps [ResultErr] with [ChaincodeDelegationError] as an error type.
   */
  data class Err<out V : Any>(
    val error: ChaincodeDelegationError,
  ) : ChaincodeDelegationResult<V>() {
    override val result: Result<V, ChaincodeDelegationError> = ResultErr(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [ChaincodeDelegationResult].
   */
  fun onSuccess(action: (V) -> Unit): ChaincodeDelegationResult<V> {
    return result.onSuccess(action).toChaincodeDelegationResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [ChaincodeDelegationResult].
   */
  fun onFailure(action: (ChaincodeDelegationError) -> Unit): ChaincodeDelegationResult<V> {
    return result.onFailure(action).toChaincodeDelegationResult()
  }
}

/**
 * Wraps [Result] into [ChaincodeDelegationResult].
 */
fun <V : Any> Result<V, ChaincodeDelegationError>.toChaincodeDelegationResult(): ChaincodeDelegationResult<V> {
  return mapBoth(
    success = { ChaincodeDelegationResult.Ok(it) },
    failure = { ChaincodeDelegationResult.Err(it) }
  )
}
