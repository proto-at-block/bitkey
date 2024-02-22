package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.serialization.DelegateSerializer
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.decodeHex

/**
 * A [surrogate](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#composite-serializer-via-surrogate)
 * for holding a class that isn't otherwise annotated with [Serializable]. In this case,
 * [AppGlobalAuthKeypair].
 *
 * Changing these field names will fail tests and then break cloud backups,
 * so proceed with caution.
 */
@Serializable
internal data class AppAuthKeypairSurrogate(
  val publicKey: String,
  val privateKeyHex: String,
)

internal object AppGlobalAuthKeypairSerializer : DelegateSerializer<AppAuthKeypairSurrogate, AppGlobalAuthKeypair>(
  AppAuthKeypairSurrogate.serializer()
) {
  override fun serialize(data: AppGlobalAuthKeypair) =
    AppAuthKeypairSurrogate(
      publicKey = data.publicKey.pubKey.value,
      privateKeyHex = data.privateKey.key.bytes.hex()
    )

  override fun deserialize(data: AppAuthKeypairSurrogate) =
    AppGlobalAuthKeypair(
      publicKey = AppGlobalAuthPublicKey(Secp256k1PublicKey(data.publicKey)),
      privateKey = AppGlobalAuthPrivateKey(Secp256k1PrivateKey(data.privateKeyHex.decodeHex()))
    )
}
