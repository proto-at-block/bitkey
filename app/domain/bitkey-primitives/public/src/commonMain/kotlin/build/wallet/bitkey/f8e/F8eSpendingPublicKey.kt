package build.wallet.bitkey.f8e

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.spending.SpendingPublicKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = F8eSpendingPublicKeySerializer::class)
data class F8eSpendingPublicKey(
  override val key: DescriptorPublicKey,
) : SpendingPublicKey {
  constructor(dpub: String) : this(DescriptorPublicKey(dpub))
}

object F8eSpendingPublicKeySerializer : KSerializer<F8eSpendingPublicKey> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("F8eSpendingPublicKey", PrimitiveKind.STRING)

  override fun serialize(
    encoder: Encoder,
    value: F8eSpendingPublicKey,
  ) {
    encoder.encodeString(value.key.dpub)
  }

  override fun deserialize(decoder: Decoder): F8eSpendingPublicKey {
    return F8eSpendingPublicKey(dpub = decoder.decodeString())
  }
}
