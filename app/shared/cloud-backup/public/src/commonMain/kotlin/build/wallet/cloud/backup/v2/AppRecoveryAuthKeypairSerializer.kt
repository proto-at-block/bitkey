package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.serialization.DelegateSerializer
import okio.ByteString.Companion.decodeHex

internal object AppRecoveryAuthKeypairSerializer : DelegateSerializer<AppAuthKeypairSurrogate, AppRecoveryAuthKeypair>(
  AppAuthKeypairSurrogate.serializer()
) {
  override fun serialize(data: AppRecoveryAuthKeypair) =
    AppAuthKeypairSurrogate(
      publicKey = data.publicKey.pubKey.value,
      privateKeyHex = data.privateKey.key.bytes.hex()
    )

  override fun deserialize(data: AppAuthKeypairSurrogate) =
    AppRecoveryAuthKeypair(
      publicKey = AppRecoveryAuthPublicKey(Secp256k1PublicKey(data.publicKey)),
      privateKey = AppRecoveryAuthPrivateKey(Secp256k1PrivateKey(data.privateKeyHex.decodeHex()))
    )
}
