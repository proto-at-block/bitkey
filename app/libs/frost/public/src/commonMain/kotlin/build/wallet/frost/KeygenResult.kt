package build.wallet.frost

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [KeygenError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class KeygenResult<out V : Any> {
  abstract val result: Result<V, KeygenError>

  /**
   * Wraps [ResultOk] with [KeygenError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : KeygenResult<V>() {
    override val result: Result<V, KeygenError> = ResultOk(value)
  }

  /**
   * Wraps [ResultErr] with [KeygenError] as an error type.
   */
  data class Err<out V : Any>(val error: KeygenError) : KeygenResult<V>() {
    override val result: Result<V, KeygenError> = ResultErr(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [KeygenResult].
   */
  fun onSuccess(action: (V) -> Unit): KeygenResult<V> {
    return result.onSuccess(action).toKeygenResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [KeygenResult].
   */
  fun onFailure(action: (KeygenError) -> Unit): KeygenResult<V> {
    return result.onFailure(action).toKeygenResult()
  }
}

/**
 * Wraps [Result] into [KeygenResult].
 */
fun <V : Any> Result<V, KeygenError>.toKeygenResult(): KeygenResult<V> {
  return mapBoth(
    success = { KeygenResult.Ok(it) },
    failure = { KeygenResult.Err(it) }
  )
}
