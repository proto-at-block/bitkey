@file:Suppress("ForbiddenImport")

package build.wallet.ktor.result

import build.wallet.catchingResult
import build.wallet.ktor.result.HttpBodyError.DoubleReceiveError
import build.wallet.ktor.result.HttpBodyError.SerializationError
import build.wallet.ktor.result.HttpBodyError.UnhandledError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import io.ktor.client.HttpClient
import io.ktor.client.call.DoubleReceiveException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import kotlinx.serialization.SerializationException
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Tries to receive the payload of the [HttpResponse] as a specific type [T].
 *
 * In most cases, it's recommended to use [HttpClient.bodyResult] to make HTTP calls and process
 * responses.
 *
 * @return [HttpBodyError.SerializationError] if failed to deserialize body from response.
 * @return [HttpBodyError.DoubleReceiveError] if body was received more than once.
 * @return [HttpBodyError.UnhandledError] if caught an exception that we don't handle.
 */
suspend inline fun <reified T> HttpResponse.bodyResult(): Result<T, HttpBodyError> {
  return catchingResult { body<T>() }
    .mapError { exception ->
      when (exception) {
        is NoTransformationFoundException -> SerializationError(exception)
        is DoubleReceiveException -> DoubleReceiveError(exception)
        is SerializationException -> SerializationError(exception)
        else -> UnhandledError(exception)
      }
    }
}

suspend fun HttpResponse.readByteString(): ByteString {
  return readBytes().toByteString()
}
