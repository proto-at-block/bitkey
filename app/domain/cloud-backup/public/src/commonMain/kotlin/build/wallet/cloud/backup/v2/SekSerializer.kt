package build.wallet.cloud.backup.v2

import bitkey.data.PrivateData
import build.wallet.cloud.backup.csek.Sek
import build.wallet.crypto.SymmetricKeyImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.ByteString.Companion.decodeHex

/**
 * Serializer for [Sek] that encodes the raw symmetric key material as a hex string.
 */
internal class SekSerializer : KSerializer<Sek> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("Sek", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Sek {
    return Sek(key = SymmetricKeyImpl(raw = decoder.decodeString().decodeHex()))
  }

  @OptIn(PrivateData::class)
  override fun serialize(
    encoder: Encoder,
    value: Sek,
  ) {
    encoder.encodeString(value.key.raw.hex())
  }
}
