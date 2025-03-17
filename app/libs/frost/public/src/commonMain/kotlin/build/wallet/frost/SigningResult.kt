package build.wallet.frost

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [SigningError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class SigningResult<out V : Any> {
  abstract val result: Result<V, SigningError>

  /**
   * Wraps [ResultOk] with [SigningError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : SigningResult<V>() {
    override val result: Result<V, SigningError> = ResultOk(value)
  }

  /**
   * Wraps [ResultErr] with [SigningError] as an error type.
   */
  data class Err<out V : Any>(val error: SigningError) : SigningResult<V>() {
    override val result: Result<V, SigningError> = ResultErr(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [SigningResult].
   */
  fun onSuccess(action: (V) -> Unit): SigningResult<V> {
    return result.onSuccess(action).toSigningResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [SigningResult].
   */
  fun onFailure(action: (SigningError) -> Unit): SigningResult<V> {
    return result.onFailure(action).toSigningResult()
  }
}

/**
 * Wraps [Result] into [SigningResult].
 */
fun <V : Any> Result<V, SigningError>.toSigningResult(): SigningResult<V> {
  return mapBoth(
    success = { SigningResult.Ok(it) },
    failure = { SigningResult.Err(it) }
  )
}
