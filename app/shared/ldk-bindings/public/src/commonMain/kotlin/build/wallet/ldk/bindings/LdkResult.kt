package build.wallet.ldk.bindings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/**
 * Wrapper for [Result] type that only uses [LdkNodeError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class LdkResult<out V : Any> {
  abstract val result: Result<V, LdkNodeError>

  /**
   * Wraps [ResultOk] with [LdkNodeError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : LdkResult<V>() {
    override val result: Result<V, LdkNodeError> = com.github.michaelbull.result.Ok(value)
  }

  /**
   * Wraps [ResultErr] with [LdkNodeError] as an error type.
   */
  data class Err<out V : Any>(val error: LdkNodeError) : LdkResult<V>() {
    override val result: Result<V, LdkNodeError> = com.github.michaelbull.result.Err(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [LdkResult].
   */
  fun onSuccess(action: (V) -> Unit): LdkResult<V> {
    return result.onSuccess(action).toLdkResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [LdkResult].
   */
  fun onFailure(action: (LdkNodeError) -> Unit): LdkResult<V> {
    return result.onFailure(action).toLdkResult()
  }
}

/**
 * Wraps [Result] into [LdkResult].
 */
fun <V : Any> Result<V, LdkNodeError>.toLdkResult(): LdkResult<V> {
  return when (this) {
    is Ok -> LdkResult.Ok(value)
    is Err -> LdkResult.Err(error)
  }
}
