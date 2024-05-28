@file:Suppress("ForbiddenImport")

package build.wallet.ktor.result

import build.wallet.catching
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import dev.zacsweers.redacted.annotations.Redacted
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.Serializable

/**
 * The base type required by [setRedactedBody] to enforce class
 * redaction which is required in place of [setBody], ensuring
 * sensitive user data does not leak into logs.
 *
 * Use [dev.zacsweers.redacted.annotations.Unredacted] to allow
 * specific properties to be logged.
 */
@Redacted
interface RedactedRequestBody

/**
 * The base type required by [bodyResult] to enforce class redaction,
 * ensuring sensitive user data does not leak into logs.
 *
 * Use [dev.zacsweers.redacted.annotations.Unredacted] to allow
 * specific properties to be logged.
 */
@Redacted
interface RedactedResponseBody

/**
 * An empty request body which conforms to [RedactedRequestBody].
 */
@Serializable
data object EmptyRequestBody : RedactedRequestBody

/**
 * An empty response body which conforms to [RedactedResponseBody].
 */
@Serializable
data object EmptyResponseBody : RedactedResponseBody

inline fun <reified T : RedactedRequestBody> HttpRequestBuilder.setRedactedBody(body: T) {
  setBody(body)
}

inline fun <reified T> HttpRequestBuilder.setUnredactedBody(body: T) {
  setBody(body)
}

/**
 * Executes [requestBody] HTTP request and tried to receive body response as type [T].
 *
 * This is the recommended way to make HTTP calls and process responses.
 */
suspend inline fun <reified T : RedactedResponseBody> HttpClient.bodyResult(
  noinline requestBody: suspend HttpClient.() -> HttpResponse,
): Result<T, NetworkingError> {
  return catching(requestBody)
    .flatMap { response -> response.bodyResult() }
}

/**
 * Executes an [HttpClient]'s request and returns [Ok] with successful [HttpResponse], or
 * [Err] where [HttpResponse] failures and thrown exceptions are mapped to [HttpError].
 *
 * In most cases, it's recommended to use [HttpClient.bodyResult] to make HTTP calls and process
 * responses.
 */
suspend fun HttpClient.catching(
  requestBody: suspend HttpClient.() -> HttpResponse,
): Result<HttpResponse, HttpError> =
  Result
    .catching { requestBody() }
    .mapError {
      when (it) {
        is IOException -> HttpError.NetworkError(it)
        else -> HttpError.UnhandledException(it)
      }
    }
    .flatMap { response ->
      val status = response.status
      when {
        status.isSuccess() -> Ok(response)
        status.isClientError -> Err(HttpError.ClientError(response))
        status.isServerError -> Err(HttpError.ServerError(response))
        else -> Err(HttpError.UnhandledError(response))
      }
    }
