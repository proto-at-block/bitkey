package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.crypto.CurveType
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
  val curveType: CurveType,
  val publicKey: String,
  val privateKeyHex: String,
)

internal object AppKeySerializer : KSerializer<AppKey> {
  override val descriptor: SerialDescriptor
    get() = AppKeySurrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): AppKey {
    val surrogate = decoder.decodeSerializableValue(AppKeySurrogate.serializer())
    return AppKeyImpl(
      curveType = surrogate.curveType,
      publicKey = PublicKey(surrogate.publicKey),
      privateKey = PrivateKey(surrogate.privateKeyHex.decodeHex())
    )
  }

  override fun serialize(
    encoder: Encoder,
    value: AppKey,
  ) {
    require(value is AppKeyImpl)
    val surrogate =
      AppKeySurrogate(
        curveType = value.curveType,
        publicKey = value.publicKey.value,
        privateKeyHex = value.privateKey!!.bytes.hex()
      )
    encoder.encodeSerializableValue(
      serializer = AppKeySurrogate.serializer(),
      value = surrogate
    )
  }
}
