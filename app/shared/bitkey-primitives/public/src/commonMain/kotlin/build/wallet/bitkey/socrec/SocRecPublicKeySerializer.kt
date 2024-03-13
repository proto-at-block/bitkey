package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal abstract class SocRecPublicKeySerializer<T : SocRecKey>(
  private val factory: (AppKey) -> T,
) : KSerializer<T> {
  override val descriptor: SerialDescriptor
    get() = PrimitiveSerialDescriptor("SocRecPublicKey", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): T =
    factory(AppKey.fromPublicKey(decoder.decodeString()))

  override fun serialize(
    encoder: Encoder,
    value: T,
  ) = encoder.encodeString(value.publicKey.value)
}
