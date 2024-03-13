package build.wallet.bitkey.socrec

import build.wallet.logging.logFailure
import build.wallet.serialization.json.JsonDecodingError
import build.wallet.serialization.json.decodeFromStringResult
import com.github.michaelbull.result.Result
import io.ktor.util.decodeBase64String
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

/**
 * [DelegatedDecryptionKey] encoded as a base64 string of its JSON representation.
 *
 * This format is used for few purposes:
 *    - storage in the app database
 *    - transport for f8e
 */
@JvmInline
@Serializable
value class EncodedTrustedContactKeyCertificate(val base64: String) {
  /**
   * Decodes [base64] from base64 to JSON representation and deserializes it into
   * a [TrustedContactKeyCertificate].
   */
  fun deserialize(): Result<TrustedContactKeyCertificate, JsonDecodingError> {
    return Json
      .decodeFromStringResult<TrustedContactKeyCertificate>(json = base64.decodeBase64String())
      .logFailure { "Error decoding certificate $this" }
  }
}
