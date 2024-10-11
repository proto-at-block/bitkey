package build.wallet.bitkey.app

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A [surrogate](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#composite-serializer-via-surrogate)
 * for holding a class that isn't otherwise annotated with [Serializable]. In this case,
 * [AppSpendingPrivateKey].
 *
 * Changing these field names will fail tests and then break cloud backups,
 * so proceed with caution.
 */
@Serializable
private data class AppSpendingPrivateKeySurrogate(
  val xprv: String,
  val mnemonics: String,
)

internal object AppSpendingPrivateKeySerializer : KSerializer<AppSpendingPrivateKey> {
  override val descriptor: SerialDescriptor
    get() = AppSpendingPrivateKeySurrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): AppSpendingPrivateKey {
    val surrogate = decoder.decodeSerializableValue(AppSpendingPrivateKeySurrogate.serializer())
    return AppSpendingPrivateKey(
      ExtendedPrivateKey(
        xprv = surrogate.xprv,
        mnemonic = surrogate.mnemonics
      )
    )
  }

  override fun serialize(
    encoder: Encoder,
    value: AppSpendingPrivateKey,
  ) {
    val surrogate =
      AppSpendingPrivateKeySurrogate(
        xprv = value.key.xprv,
        mnemonics = value.key.mnemonic
      )
    encoder.encodeSerializableValue(AppSpendingPrivateKeySurrogate.serializer(), surrogate)
  }
}
