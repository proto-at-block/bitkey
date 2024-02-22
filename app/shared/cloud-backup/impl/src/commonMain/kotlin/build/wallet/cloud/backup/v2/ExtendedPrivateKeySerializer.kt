package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A [surrogate](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#composite-serializer-via-surrogate)
 * for holding a class that isn't otherwise annotated with [Serializable]. In this case,
 * [ExtendedPrivateKey].
 *
 * Changing these field names will fail tests and then break cloud backups,
 * so proceed with caution.
 */
@Serializable
private data class ExtendedPrivateKeySurrogate(
  val xprv: String,
  val mnemonics: String,
)

object ExtendedPrivateKeySerializer : KSerializer<ExtendedPrivateKey> {
  override val descriptor: SerialDescriptor
    get() = ExtendedPrivateKeySurrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): ExtendedPrivateKey {
    val surrogate = decoder.decodeSerializableValue(ExtendedPrivateKeySurrogate.serializer())
    return ExtendedPrivateKey(
      xprv = surrogate.xprv,
      mnemonic = surrogate.mnemonics
    )
  }

  override fun serialize(
    encoder: Encoder,
    value: ExtendedPrivateKey,
  ) {
    val surrogate =
      ExtendedPrivateKeySurrogate(
        xprv = value.xprv,
        mnemonics = value.mnemonic
      )
    encoder.encodeSerializableValue(ExtendedPrivateKeySurrogate.serializer(), surrogate)
  }
}
