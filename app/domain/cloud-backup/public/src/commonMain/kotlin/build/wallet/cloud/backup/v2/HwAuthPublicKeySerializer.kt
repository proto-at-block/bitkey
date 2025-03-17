package build.wallet.cloud.backup.v2

import bitkey.serialization.DelegateSerializer
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import kotlinx.serialization.builtins.serializer

/**
 * Serializes and deserializes [HwAuthPublicKey].
 *
 * Alterations to this class can fail test and break cloud backups,
 * so proceed with caution.
 */
object HwAuthPublicKeySerializer : DelegateSerializer<String, HwAuthPublicKey>(
  String.serializer()
) {
  override fun serialize(data: HwAuthPublicKey): String = data.pubKey.value

  override fun deserialize(data: String): HwAuthPublicKey =
    HwAuthPublicKey(Secp256k1PublicKey(data))
}
