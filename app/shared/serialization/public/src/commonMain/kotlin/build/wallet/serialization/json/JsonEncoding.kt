package build.wallet.serialization.json

import build.wallet.catchingResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Runtime safe extension around [Json.encodeToString]. Returns [Err] instead of throwing in case of
 * JSON encoding error. This is the preferred way for encoding values to JSON.
 */
inline fun <reified T> Json.encodeToStringResult(value: T): Result<String, JsonEncodingError> {
  return catchingResult { encodeToString(value) }
    .mapError { JsonEncodingError(it) }
}

/**
 * Runtime safe extension around [Json.decodeFromString]. Returns [Err] instead of throwing in case of
 * JSON decoding error. This is the preferred way for decoding JSON to values.
 */
inline fun <reified T> Json.decodeFromStringResult(json: String): Result<T, JsonDecodingError> {
  return catchingResult { decodeFromString<T>(json) }
    .mapError { JsonDecodingError(it) }
}
