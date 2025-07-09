package build.wallet.encrypt

import bitkey.serialization.json.decodeFromStringResult
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XSealedData.Format
import com.github.michaelbull.result.getOrElse
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
    // Validate that the format and publicKey presence are consistent using require
    require((header.format == Format.Standard && publicKey == null) || (header.format == Format.WithPubkey && publicKey != null)) {
      when (header.format) {
        Format.Standard -> "Public key must be null for Standard format"
        Format.WithPubkey -> "Public key must not be null for WithPubkey format"
      }
    }

    val encodedHeader = Json.encodeToString(header).encodeUtf8().base64NoPad()
    val baseCiphertext = "$encodedHeader.${ciphertext.base64NoPad()}.${nonce.bytes.base64NoPad()}"
    // Only append the publicKey part if the format indicates its presence
    val publicKeyPart = if (header.format == Format.WithPubkey) ".${publicKey!!.value.decodeHex().base64NoPad()}" else ""
    return XCiphertext(baseCiphertext + publicKeyPart)
  }

  private fun ByteString.base64NoPad() = base64().replace("=", "")

  /**
   * Metadata header describing the contents of the sealed data.
   */
  @Serializable
  data class Header(
    @SerialName("v")
    @Serializable(with = XSealedDataFormatSerializer::class)
    val format: Format = Format.Standard,
    @SerialName("alg")
    val algorithm: String,
  )

  /**
   * Represents the format of the sealed data.
   *
   * Historically, this was called "version" and was serialized as "v" in the JSON header.
   * The numeric values (1 and 2) are preserved for backwards compatibility.
   */
  enum class Format(val formatCode: Int) {
    /** header.ciphertext.nonce */
    Standard(1),

    /** header.ciphertext.nonce.publicKey */
    WithPubkey(2),
  }
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
  val decodedHeader = Json.decodeFromStringResult<XSealedData.Header>(headerPart).getOrElse {
    throw IllegalArgumentException(it)
  }

  // Basic checks on parts size based on format
  when (decodedHeader.format) {
    Format.Standard -> {
      require(parts.size == 3) {
        "Expected format for Standard format: header.ciphertext.nonce"
      }
    }
    Format.WithPubkey -> {
      require(parts.size == 4) {
        "Expected format for WithPubkey format: header.ciphertext.nonce.publicKey"
      }
    }
  }

  val ciphertext = parts[1].decodeBase64() ?: throw IllegalArgumentException("Invalid base64 ciphertext: ${parts[1]}")
  val nonce = XNonce(parts[2].decodeBase64() ?: throw IllegalArgumentException("Invalid base64 nonce: ${parts[2]}"))
  val publicKey = when (decodedHeader.format) {
    Format.Standard -> null
    Format.WithPubkey -> parts[3].decodeBase64()?.hex()
      ?.let { PublicKey<Nothing>(it) }
  }

  return XSealedData(
    header = decodedHeader,
    ciphertext = ciphertext,
    nonce = nonce,
    publicKey = publicKey
  )
}
