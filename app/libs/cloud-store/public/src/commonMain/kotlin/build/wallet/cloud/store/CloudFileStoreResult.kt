package build.wallet.cloud.store

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [CloudError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class CloudFileStoreResult<out V : Any> {
  abstract val result: Result<V, CloudError>

  /**
   * Wraps [Result.Ok] with [CloudError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : CloudFileStoreResult<V>() {
    override val result: Result<V, CloudError> = ResultOk(value)
  }

  /**
   * Wraps [Result.Err] with [CloudError] as an error type.
   */
  data class Err<V : Any>(val error: CloudError) : CloudFileStoreResult<V>() {
    override val result: Result<V, CloudError> = ResultErr(error)
  }
}

/**
 * Wraps [Result] into [CloudFileStoreResult].
 */
fun <V : Any> Result<V, CloudError>.toCloudFileStoreResult(): CloudFileStoreResult<V> {
  return mapBoth(
    success = { CloudFileStoreResult.Ok(it) },
    failure = { CloudFileStoreResult.Err(it) }
  )
}
