package build.wallet.encrypt

import build.wallet.crypto.PublicKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

/**
 * The result of encrypting a message with XChaCha20Poly1305. The tag is
 * included in the ciphertext.
 *
 * This class is considered `internal`, and is exposed only so it can be used in iOS native code.
 */
data class XSealedData(
  val header: Header,
  val ciphertext: ByteString,
  val nonce: XNonce,
  val publicKey: PublicKey<*>? = null,
) {
  /**
   * Encode this `XSealedData` as a `XCiphertext`.
   *
   * The header is first JSON encoded. Then each component (header, ciphertext, nonce) are
   * individually base64 encoded and concatenated using a period (`.`) as a separator.
   */
  fun toOpaqueCiphertext(): XCiphertext {
    // Validate that the version and publicKey presence are consistent using require
    require((header.version == 1 && publicKey == null) || (header.version == 2 && publicKey != null)) {
      when (header.version) {
        1 -> "Public key must be null for version 1"
        2 -> "Public key must not be null for version 2"
        else -> "Unsupported version: ${header.version}"
      }
    }

    val encodedHeader = Json.encodeToString(header).encodeUtf8().base64NoPad()
    val baseCiphertext = "$encodedHeader.${ciphertext.base64NoPad()}.${nonce.bytes.base64NoPad()}"
    // Only append the publicKey part if the version indicates its presence
    val publicKeyPart = if (header.version >= 2) ".${publicKey!!.value.decodeHex().base64NoPad()}" else ""
    return XCiphertext(baseCiphertext + publicKeyPart)
  }

  private fun ByteString.base64NoPad() = base64().replace("=", "")

  /**
   * Metadata header describing the contents of the sealed data.
   */
  @Serializable
  data class Header(
    @SerialName("v")
    val version: Int = 1,
    @SerialName("alg")
    val algorithm: String,
  )
}

/**
 * Decode a `XSealedData` from a `XCiphertext`.
 *
 * This function is considered `internal`, and is exposed only so it can be used in iOS native code.
 */
@Throws(IllegalArgumentException::class)
@Suppress("ThrowsCount")
fun XCiphertext.toXSealedData(): XSealedData {
  val parts = value.split(".")
  val headerPart = parts[0].decodeBase64()?.utf8() ?: throw IllegalArgumentException("Invalid base64 header: ${parts[0]}")
  val decodedHeader = Json.decodeFromString<XSealedData.Header>(headerPart)

  // Basic checks on parts size based on version
  if (decodedHeader.version == 1) {
    require(parts.size == 3) {
      "Expected format for version 1: header.ciphertext.nonce"
    }
  }
  if (decodedHeader.version >= 2) {
    require(parts.size == 4) {
      "Expected format for version 2 or higher: header.ciphertext.nonce.publicKey"
    }
  }

  val ciphertext = parts[1].decodeBase64() ?: throw IllegalArgumentException("Invalid base64 ciphertext: ${parts[1]}")
  val nonce = XNonce(parts[2].decodeBase64() ?: throw IllegalArgumentException("Invalid base64 nonce: ${parts[2]}"))
  val publicKey = if (decodedHeader.version >= 2) {
    parts[3].decodeBase64()?.hex()
      ?.let { PublicKey<Nothing>(it) }
  } else {
    null
  }

  return XSealedData(
    header = decodedHeader,
    ciphertext = ciphertext,
    nonce = nonce,
    publicKey = publicKey
  )
}
