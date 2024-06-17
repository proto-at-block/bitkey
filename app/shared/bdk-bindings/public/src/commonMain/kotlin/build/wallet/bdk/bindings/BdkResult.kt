package build.wallet.bdk.bindings

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [BdkError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class BdkResult<out V : Any> {
  abstract val result: Result<V, BdkError>

  /**
   * Wraps [ResultOk] with [BdkError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : BdkResult<V>() {
    override val result: Result<V, BdkError> = ResultOk(value)
  }

  /**
   * Wraps [ResultErr] with [BdkError] as an error type.
   */
  data class Err<out V : Any>(val error: BdkError) : BdkResult<V>() {
    override val result: Result<V, BdkError> = ResultErr(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [BdkResult].
   */
  fun onSuccess(action: (V) -> Unit): BdkResult<V> {
    return result.onSuccess(action).toBdkResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [BdkResult].
   */
  fun onFailure(action: (BdkError) -> Unit): BdkResult<V> {
    return result.onFailure(action).toBdkResult()
  }
}

/**
 * Wraps [Result] into [BdkResult].
 */
fun <V : Any> Result<V, BdkError>.toBdkResult(): BdkResult<V> {
  return mapBoth(
    success = { BdkResult.Ok(it) },
    failure = { BdkResult.Err(it) }
  )
}
