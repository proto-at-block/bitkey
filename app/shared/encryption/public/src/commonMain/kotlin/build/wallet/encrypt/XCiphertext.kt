package build.wallet.encrypt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The result of encrypting a message with XChaCha20Poly1305. The ciphertext is an opaque string
 * that includes the ciphertext, nonce, and tag.
 */
@Serializable(with = XCiphertext.Serializer::class)
data class XCiphertext(val value: String) {
  class Serializer : KSerializer<XCiphertext> {
    override val descriptor: SerialDescriptor
      get() = PrimitiveSerialDescriptor("XCiphertext", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): XCiphertext = XCiphertext(decoder.decodeString())

    override fun serialize(
      encoder: Encoder,
      value: XCiphertext,
    ) = encoder.encodeString(value.value)
  }
}
