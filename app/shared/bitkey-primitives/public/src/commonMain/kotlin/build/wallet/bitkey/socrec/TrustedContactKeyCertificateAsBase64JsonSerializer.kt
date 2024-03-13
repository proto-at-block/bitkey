package build.wallet.bitkey.socrec

import com.github.michaelbull.result.getOrThrow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TrustedContactKeyCertificateAsBase64JsonSerializer :
  KSerializer<TrustedContactKeyCertificate> {
  override val descriptor: SerialDescriptor get() = TrustedContactKeyCertificate.serializer().descriptor

  override fun deserialize(decoder: Decoder): TrustedContactKeyCertificate {
    val base64 = decoder.decodeString()
    return EncodedTrustedContactKeyCertificate(base64).deserialize().getOrThrow()
  }

  override fun serialize(
    encoder: Encoder,
    value: TrustedContactKeyCertificate,
  ) {
    encoder.encodeString(value.encode().getOrThrow().base64)
  }
}
