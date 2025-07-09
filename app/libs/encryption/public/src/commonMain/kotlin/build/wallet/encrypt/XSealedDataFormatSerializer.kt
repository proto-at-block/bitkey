package build.wallet.encrypt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for the [XSealedData.Format] enum that handles the conversion between enum values
 * and their numeric representation in JSON.
 *
 * This serializer maintains backwards compatibility with the original "version" field
 * that was serialized as "v" with numeric values 1 or 2. The serializer:
 * - Serializes enum values to their corresponding [XSealedData.Format.formatCode] integers
 * - Deserializes integers back to the matching enum value
 * - Throws [IllegalArgumentException] for invalid format numbers
 */
object XSealedDataFormatSerializer : KSerializer<XSealedData.Format> {
  override val descriptor = PrimitiveSerialDescriptor("Format", PrimitiveKind.INT)

  override fun serialize(
    encoder: Encoder,
    value: XSealedData.Format,
  ) {
    encoder.encodeInt(value.formatCode)
  }

  override fun deserialize(decoder: Decoder): XSealedData.Format {
    val num = decoder.decodeInt()
    return XSealedData.Format.entries.find {
      it.formatCode == num
    } ?: throw IllegalArgumentException("Invalid format number: $num")
  }
}
