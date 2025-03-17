package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.ByteString.Companion.decodeHex

/**
 * Surrogate and serializer for [AppKey] that serializes into a JSON object containing the
 * private and public keys.
 */
@Serializable
internal data class AppKeySurrogate(
  val publicKey: String,
  val privateKeyHex: String,
)

internal class AppKeyKeyPairSerializer<T : KeyPurpose> : KSerializer<AppKey<T>> {
  override val descriptor: SerialDescriptor
    get() = AppKeySurrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): AppKey<T> {
    val surrogate = decoder.decodeSerializableValue(AppKeySurrogate.serializer())
    return AppKey(
      // TODO: Move curve type into generics
      publicKey = PublicKey(surrogate.publicKey),
      privateKey = PrivateKey(surrogate.privateKeyHex.decodeHex())
    )
  }

  override fun serialize(
    encoder: Encoder,
    value: AppKey<T>,
  ) {
    val surrogate =
      AppKeySurrogate(
        publicKey = value.publicKey.value,
        privateKeyHex = value.privateKey.bytes.hex()
      )
    encoder.encodeSerializableValue(
      serializer = AppKeySurrogate.serializer(),
      value = surrogate
    )
  }
}
