package build.wallet.cloud.backup

import bitkey.serialization.json.JsonDecodingError
import bitkey.serialization.json.JsonEncodingError
import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import kotlinx.serialization.json.Json

/**
 * Injectable JSON serializer for cloud backup and SocRec operations.
 * Provides type-safe JSON encoding/decoding with forward compatibility.
 */
@BitkeyInject(AppScope::class)
class JsonSerializer {
  val json: Json = Json {
    ignoreUnknownKeys = true
  }

  inline fun <reified T> encodeToStringResult(value: T): Result<String, JsonEncodingError> {
    return json.encodeToStringResult(value)
  }

  inline fun <reified T> decodeFromStringResult(jsonString: String): Result<T, JsonDecodingError> {
    return json.decodeFromStringResult(jsonString)
  }
}
