package bitkey.f8e.error

import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.GeneralClientErrorCode
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.get

/**
 * An error type for mapping [HttpError] to specific F8e errors, allowing for specific
 * error codes for client errors (4xx) to be specified for a given request.
 */
sealed interface F8eError<T : F8eClientErrorCode> {
  val error: Error

  /** Indicates there was a network connectivity error */
  data class ConnectivityError<T : F8eClientErrorCode>(
    override val error: HttpError.NetworkError,
  ) : F8eError<T>

  /** Indicates there was a general client (4xx) error */
  data class GeneralClientError<T : F8eClientErrorCode>(
    override val error: HttpError.ClientError,
    val errorCode: GeneralClientErrorCode,
  ) : F8eError<T>

  /** Indicates there was a specific client (4xx) error of type T */
  data class SpecificClientError<T : F8eClientErrorCode>(
    override val error: HttpError.ClientError,
    val errorCode: T,
  ) : F8eError<T>

  /** Indicates there was a server (5xx) error */
  data class ServerError<T : F8eClientErrorCode>(
    override val error: HttpError.ServerError,
  ) : F8eError<T>

  /** Indicates there was a general unhandled error */
  data class UnhandledError<T : F8eClientErrorCode>(
    override val error: Error,
  ) : F8eError<T>

  /** Indicates there was a general unhandled exception */
  data class UnhandledException<T : F8eClientErrorCode>(
    override val error: HttpError.UnhandledException,
  ) : F8eError<T>
}

suspend inline fun <reified T : F8eClientErrorCode> NetworkingError.toF8eError(): F8eError<T> {
  return when (this) {
    // If we got a client (4xx) error, try to deserialize the error to an error code we know about
    is HttpError.ClientError -> deserializeToF8eError()
    is HttpError.NetworkError -> F8eError.ConnectivityError(error = this)
    is HttpError.ServerError -> F8eError.ServerError(error = this)
    is HttpError.UnhandledError -> F8eError.UnhandledError(error = this)
    is HttpError.UnhandledException -> F8eError.UnhandledException(error = this)
    else -> F8eError.UnhandledError(error = this)
  }
}

suspend inline fun <reified T : F8eClientErrorCode> HttpError.ClientError.deserializeToF8eError(): F8eError<T> {
  // First, try to deserialize to the specified error code, T.
  // Note: we only expect one error to be returned from F8
  val errorOfTypeT =
    response.bodyResult<F8eClientErrorResponse<T>>()
      .get()?.errors?.firstOrNull()?.code
  if (errorOfTypeT != null) {
    return F8eError.SpecificClientError(error = this, errorCode = errorOfTypeT)
  }

  // Next, try to deserialize to a general error code, [GeneralClientErrorCode]
  val generalError =
    response.bodyResult<F8eClientErrorResponse<GeneralClientErrorCode>>()
      .get()?.errors?.firstOrNull()?.code
  if (generalError != null) {
    return F8eError.GeneralClientError(error = this, errorCode = generalError)
  }

  // The error doesn't map to any code we know about, return [UnhandledError]
  return F8eError.UnhandledError(error = this)
}
