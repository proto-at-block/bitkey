package build.wallet.encrypt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a public key for the Secp256k1 elliptic curve.
 *
 * @property value The string representation of the public key.
 */
@Serializable(with = Secp256k1PublicKey.Serializer::class)
data class Secp256k1PublicKey(val value: String) {
  class Serializer : KSerializer<Secp256k1PublicKey> {
    override val descriptor: SerialDescriptor
      get() = PrimitiveSerialDescriptor("Secp256k1PublicKey", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Secp256k1PublicKey =
      Secp256k1PublicKey(decoder.decodeString())

    override fun serialize(
      encoder: Encoder,
      value: Secp256k1PublicKey,
    ) = encoder.encodeString(value.value)
  }
}
