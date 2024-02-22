package build.wallet.platform.data

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * Wrapper for [Result] type that only uses [FileManagerError] for errors.
 *
 * Primary used for easier Swift interop. With Kotlin/Native, most of the generics get erased
 * in generated ObjC code, which makes consuming code with generics in Swift hard and tedious.
 */
sealed class FileManagerResult<out V : Any> {
  abstract val result: Result<V, FileManagerError>

  /**
   * Wraps [Result.Ok] with [FileManagerError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : FileManagerResult<V>() {
    override val result: Result<V, FileManagerError> = ResultOk(value)
  }

  /**
   * Wraps [Result.Err] with [FileManagerError] as an error type.
   */
  data class Err<V : Any>(val error: FileManagerError) : FileManagerResult<V>() {
    override val result: Result<V, FileManagerError> = ResultErr(error)
  }
}

/**
 * Wraps [Result] into [FileManagerResult].
 */
fun <V : Any> Result<V, FileManagerError>.toFileManagerResult(): FileManagerResult<V> {
  return when (this) {
    is ResultOk -> FileManagerResult.Ok(value)
    is ResultErr -> FileManagerResult.Err(error)
  }
}
