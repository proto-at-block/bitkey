package build.wallet.notifications

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class DeviceTokenManagerResult<out V : Any, out E : Error> {
  abstract val result: Result<V, E>

  /**
   * Wraps [ResultOk] with [DeviceTokenManagerResult] as an error type.
   */
  data class Ok<out V : Any, E : Error>(val value: V) : DeviceTokenManagerResult<V, E>() {
    override val result: Result<V, E> = ResultOk(value)
  }

  /**
   * Wraps [ResultErr] with [DeviceTokenManagerResult] as an error type.
   */
  data class Err<out V : Any, E : Error>(val error: E) : DeviceTokenManagerResult<V, E>() {
    override val result: Result<V, E> = ResultErr(error)
  }

  /**
   * Alias for [Result.get].
   */
  fun get(): V? = result.get()

  /**
   * Alias for [Result.onSuccess], wraps the result back into [DeviceTokenManagerResult].
   */
  fun onSuccess(action: (V) -> Unit): DeviceTokenManagerResult<V, E> {
    return result.onSuccess(action).toDeviceTokenManagerResult()
  }

  /**
   * Alias for [Result.onSuccess], wraps the result back into [DeviceTokenManagerResult].
   */
  fun onFailure(action: (E) -> Unit): DeviceTokenManagerResult<V, E> {
    return result.onFailure(action).toDeviceTokenManagerResult()
  }
}

/**
 * Wraps [Result] into [DeviceTokenManagerResult].
 */
fun <V : Any, E : Error> Result<V, E>.toDeviceTokenManagerResult(): DeviceTokenManagerResult<V, E> {
  return when (this) {
    is ResultOk -> DeviceTokenManagerResult.Ok(value)
    is ResultErr -> DeviceTokenManagerResult.Err(error)
  }
}
